/**
 ** Copyright (c) 2010 Ushahidi Inc
 ** All rights reserved
 ** Contact: team@ushahidi.com
 ** Website: http://www.ushahidi.com
 **
 ** GNU Lesser General Public License Usage
 ** This file may be used under the terms of the GNU Lesser
 ** General Public License version 3 as published by the Free Software
 ** Foundation and appearing in the file LICENSE.LGPL included in the
 ** packaging of this file. Please review the following information to
 ** ensure the GNU Lesser General Public License version 3 requirements
 ** will be met: http://www.gnu.org/licenses/lgpl.html.
 **
 **
 ** If you have questions regarding the use of this file, please contact
 ** Ushahidi developers at team@ushahidi.com.
 **
 **/

package com.ushahidi.android.app.ui.phone;

import java.io.File;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Vector;

import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.app.Dialog;
import android.app.TimePickerDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.location.Location;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.support.v4.view.MenuItem;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.text.format.DateFormat;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.MenuInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.DatePicker;
import android.widget.ImageSwitcher;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TimePicker;
import android.widget.ViewSwitcher;

import com.ushahidi.android.app.ImageManager;
import com.ushahidi.android.app.R;
import com.ushahidi.android.app.activities.BaseEditMapActivity;
import com.ushahidi.android.app.adapters.UploadPhotoAdapter;
import com.ushahidi.android.app.entities.Category;
import com.ushahidi.android.app.entities.Report;
import com.ushahidi.android.app.models.AddReportModel;
import com.ushahidi.android.app.models.ListReportModel;
import com.ushahidi.android.app.tasks.GeocoderTask;
import com.ushahidi.android.app.util.PhotoUtils;
import com.ushahidi.android.app.util.Util;
import com.ushahidi.android.app.views.AddReportView;

/**
 * @author eyedol
 */
public class AddReportActivity extends
		BaseEditMapActivity<AddReportView, AddReportModel> implements
		OnClickListener, ViewSwitcher.ViewFactory, OnItemClickListener {

	private ReverseGeocoderTask reverseGeocoderTask;

	private static final int DIALOG_ERROR_NETWORK = 0;

	private static final int DIALOG_ERROR_SAVING = 1;

	private static final int DIALOG_CHOOSE_IMAGE_METHOD = 2;

	private static final int DIALOG_MULTIPLE_CATEGORY = 3;

	private static final int TIME_DIALOG_ID = 4;

	private static final int DATE_DIALOG_ID = 5;

	private static final int DIALOG_SHOW_MESSAGE = 6;

	private static final int DIALOG_SHOW_REQUIRED = 7;

	private static final int DIALOG_SHOW_PROMPT = 8;
	
	private static final int DIALOG_SHOW_DELETE_PROMPT = 9;

	private static final int REQUEST_CODE_CAMERA = 0;

	private static final int REQUEST_CODE_IMAGE = 1;

	private Calendar mCalendar;

	private String mDateToSubmit = "";

	private int mCategoryLength;

	private Vector<String> mVectorCategories = new Vector<String>();

	private Vector<String> mCategoriesId = new Vector<String>();

	private HashMap<String, String> mCategoriesTitle = new HashMap<String, String>();

	private boolean mError = false;

	private int id = 0;

	private int mCounter = 0;

	private UploadPhotoAdapter pendingPhoto;

	private String mErrorMessage;

	private String photoName;

	private ListReportModel reportModel;
	
	public AddReportActivity() {
		super(AddReportView.class, R.layout.add_report, R.menu.add_report,
				R.id.location_map);
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		view.mLatitude.addTextChangedListener(latLonTextWatcher);
		view.mLongitude.addTextChangedListener(latLonTextWatcher);
		mapController = view.mapView.getController();
		view.mBtnPicture.setOnClickListener(this);
		view.mBtnAddCategory.setOnClickListener(this);
		view.mPickDate.setOnClickListener(this);
		view.mPickTime.setOnClickListener(this);
		mCalendar = Calendar.getInstance();
		pendingPhoto = new UploadPhotoAdapter(this);
		view.gallery.setAdapter(pendingPhoto);
		view.gallery.setOnItemClickListener(this);
		view.mSwitcher.setFactory(this);
		this.id = getIntent().getExtras().getInt("id", 0);
		reportModel = new ListReportModel();
		// edit existing report
		if (id > 0) {

			// make the delete button visible because we're editing
			view.mDeleteReport.setOnClickListener(this);
			view.mDeleteReport.setVisibility(View.VISIBLE);
			pendingPhoto.refresh();
			setSavedReport(id);
		} else {
			// add a new report
			updateDisplay();
			pendingPhoto.refresh();
		}

		registerForContextMenu(view.gallery);
	}

	@Override
	protected void onStart() {
		super.onStart();
	}

	@Override
	protected void onPause() {
		super.onPause();
		if (reverseGeocoderTask != null) {
			reverseGeocoderTask.cancel(true);
		}
	}

	/**
	 * Upon being resumed we can retrieve the current state. This allows us to
	 * update the state if it was changed at any time while paused.
	 */
	@Override
	protected void onResume() {
		getSharedText();
		super.onResume();
	}

	// Context Menu Stuff
	@Override
	public void onCreateContextMenu(ContextMenu menu, View v,
			ContextMenuInfo menuInfo) {
		super.onCreateContextMenu(menu, v, menuInfo);
		new MenuInflater(this).inflate(R.menu.photo_context, menu);

	}

	@Override
	public boolean onContextItemSelected(android.view.MenuItem item) {
		AdapterView.AdapterContextMenuInfo info = (AdapterView.AdapterContextMenuInfo) item
				.getMenuInfo();
		boolean result = performAction(item, info.position);

		if (!result) {
			result = super.onContextItemSelected(item);
		}

		return result;

	}

	public boolean performAction(android.view.MenuItem item, int position) {

		if (item.getItemId() == R.id.remove_photo) {
			// Delete by name
			if (ImageManager.deletePendingPhoto(this,
					pendingPhoto.getItem(position).getPhoto())) {
				pendingPhoto.refresh();
			}
			return true;

		}
		return false;

	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		if (item.getItemId() == android.R.id.home) {
			showDialog();
			return true;

		} else if (item.getItemId() == R.id.menu_send) {
			validateReports();
			return true;
		} else if (item.getItemId() == R.id.menu_clear) {
			showDialog(DIALOG_SHOW_PROMPT);
			return true;
		}
		return super.onOptionsItemSelected(item);

	}

	@Override
	public void onClick(View button) {
		if (button.getId() == R.id.btnPicture) {
			// get a file name for the photo to be uploaded
			photoName = Util.getDateTime() + ".jpg";
			showDialog(DIALOG_CHOOSE_IMAGE_METHOD);

		} else if (button.getId() == R.id.add_category) {
			showDialog(DIALOG_MULTIPLE_CATEGORY);
			mCounter++;
		} else if (button.getId() == R.id.pick_date) {
			showDialog(DATE_DIALOG_ID);
		} else if (button.getId() == R.id.pick_time) {
			showDialog(TIME_DIALOG_ID);
		}else if(button.getId() == R.id.delete_report) {
			showDialog(DIALOG_SHOW_DELETE_PROMPT);
		}
		
	}

	private void validateReports() {
		// Dipo Fix
		mError = false;
		boolean required = false;
		// @inoran
		// validate the title field
		if (TextUtils.isEmpty(view.mIncidentTitle.getText())) {
			mErrorMessage = getString(R.string.title) + "\n";
			required = true;

		} else if (view.mIncidentTitle.getText().length() < 3
				|| view.mIncidentTitle.getText().length() > 200) {
			mErrorMessage = getString(R.string.less_report_title) + "\n";
			mError = true;
		}

		if (TextUtils.isEmpty(view.mIncidentDesc.getText())) {
			mErrorMessage += getString(R.string.description) + "\n";
			required = true;
		}

		// Dipo Fix
		if (mVectorCategories.size() == 0) {
			mErrorMessage += getString(R.string.category) + "\n";
			required = true;
		}

		// validate lat long
		if (TextUtils.isEmpty(view.mLatitude.getText().toString())) {
			mErrorMessage += getString(R.string.latitude) + "\n";
			required = true;
		} else {

			try {
				Double.parseDouble(view.mLatitude.getText().toString());
			} catch (NumberFormatException ex) {
				mErrorMessage += getString(R.string.invalid_latitude) + "\n";
				mError = true;
			}
		}

		// validate lat long
		if (TextUtils.isEmpty(view.mLongitude.getText().toString())) {
			mErrorMessage += getString(R.string.longitude) + "\n";
			mError = true;
		} else {

			try {
				Double.parseDouble(view.mLongitude.getText().toString());
			} catch (NumberFormatException ex) {
				mErrorMessage += getString(R.string.invalid_longitude) + "\n";
				mError = true;
			}
		}

		// validate location
		if (TextUtils.isEmpty(view.mIncidentLocation.getText())) {
			mErrorMessage += getString(R.string.location);
			required = true;
		}

		if (required) {
			showDialog(DIALOG_SHOW_REQUIRED);
		} else if (mError) {
			showDialog(DIALOG_SHOW_MESSAGE);
		} else {
			new SaveTask(this).execute((String) null);

		}
	}

	/**
	 * Post to local database
	 * 
	 * @author henryaddo
	 */
	private boolean addReport() {
		log("Adding new reports to");
		ListReportModel reportModel = new ListReportModel();
		File[] pendingPhotos = PhotoUtils.getPendingPhotos(this);

		Report report = new Report();

		report.setTitle(view.mIncidentTitle.getText().toString());
		report.setDescription(view.mIncidentDesc.getText().toString());
		report.setLatitude(view.mLongitude.getText().toString());
		report.setLongitude(view.mLatitude.getText().toString());
		report.setLocationName(view.mIncidentLocation.getText().toString());
		report.setReportDate(mDateToSubmit);
		report.setMode(String.valueOf(0));
		report.setVerified(String.valueOf(0));
		report.setPending(1);

		if (reportModel.addPendingReport(report, mVectorCategories,
				pendingPhotos, view.mNews.getText().toString())) {
			// move saved photos
			log("Moving photos to fetched folder");
			ImageManager.movePendingPhotos(this);
			return true;
		}
		return false;

	}

	/**
	 * Edit existing report
	 * 
	 * @author henryaddo
	 */
	private void setSavedReport(int id) {
		log("Adding new reports to");
		
		File[] pendingPhotos = PhotoUtils.getPendingPhotos(this);

		// set text part of reports
		view.mIncidentTitle.setText("");
		view.mIncidentDesc.setText("");
		view.mLongitude.setText("");
		view.mLatitude.setText("");
		view.mIncidentLocation.setText("");

		// set date and time
		setDateAndTime("");
		// set the photos

		// set news
		view.mNews.setText("");
	}
	
	private void deleteReport() {
		//make sure it's and existing report
		if(id > 0 ) {
			if(reportModel.deleteReport(id)) {
				finish();
			}
		}
	}

	/**
	 * Create various dialog
	 */
	@Override
	protected Dialog onCreateDialog(int id) {
		switch (id) {
		case DIALOG_ERROR_NETWORK: {
			AlertDialog dialog = (new AlertDialog.Builder(this)).create();
			dialog.setTitle(getString(R.string.network_error));
			dialog.setMessage(getString(R.string.network_error_msg));
			dialog.setButton2(getString(R.string.btn_ok),
					new Dialog.OnClickListener() {
						public void onClick(DialogInterface dialog, int which) {
							dialog.dismiss();
						}
					});
			dialog.setCancelable(false);
			return dialog;
		}
		case DIALOG_ERROR_SAVING: {
			AlertDialog dialog = (new AlertDialog.Builder(this)).create();
			dialog.setTitle(getString(R.string.network_error));
			dialog.setMessage(getString(R.string.file_system_error_msg));
			dialog.setButton2(getString(R.string.btn_ok),
					new Dialog.OnClickListener() {
						public void onClick(DialogInterface dialog, int which) {
							dialog.dismiss();
						}
					});
			dialog.setCancelable(false);
			return dialog;
		}

		case DIALOG_CHOOSE_IMAGE_METHOD: {

			AlertDialog dialog = (new AlertDialog.Builder(this)).create();
			dialog.setTitle(getString(R.string.choose_method));
			dialog.setMessage(getString(R.string.how_to_select_pic));
			dialog.setButton(getString(R.string.gallery_option),
					new Dialog.OnClickListener() {
						public void onClick(DialogInterface dialog, int which) {
							Intent intent = new Intent();
							intent.setAction(Intent.ACTION_PICK);
							intent.setData(MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
							startActivityForResult(intent, REQUEST_CODE_IMAGE);
							dialog.dismiss();
						}
					});
			dialog.setButton2(getString(R.string.btn_cancel),
					new Dialog.OnClickListener() {
						public void onClick(DialogInterface dialog, int which) {
							dialog.dismiss();
						}
					});
			dialog.setButton3(getString(R.string.camera_option),
					new Dialog.OnClickListener() {
						public void onClick(DialogInterface dialog, int which) {
							Intent intent = new Intent(
									android.provider.MediaStore.ACTION_IMAGE_CAPTURE);
							intent.putExtra(MediaStore.EXTRA_OUTPUT, PhotoUtils
									.getPhotoUri(photoName,
											AddReportActivity.this));
							startActivityForResult(intent, REQUEST_CODE_CAMERA);
							dialog.dismiss();
						}
					});

			dialog.setCancelable(false);
			return dialog;
		}

		case DIALOG_MULTIPLE_CATEGORY: {
			if (showCategories() != null) {
				return new AlertDialog.Builder(this)
						.setTitle(R.string.add_categories)
						.setMultiChoiceItems(
								showCategories(),
								null,
								new DialogInterface.OnMultiChoiceClickListener() {
									public void onClick(DialogInterface dialog,
											int whichButton, boolean isChecked) {
										// see if categories have previously

										if (isChecked) {
											mVectorCategories.add(mCategoriesId
													.get(whichButton));
											mError = false;
										} else {
											mVectorCategories
													.remove(mCategoriesId
															.get(whichButton));
										}

										setSelectedCategories(mVectorCategories);
									}
								})
						.setPositiveButton(R.string.btn_ok,
								new DialogInterface.OnClickListener() {
									public void onClick(DialogInterface dialog,
											int whichButton) {

										/* User clicked Yes so do some stuff */
									}
								}).create();
			}
		}

		case TIME_DIALOG_ID:
			return new TimePickerDialog(this, mTimeSetListener,
					mCalendar.get(Calendar.HOUR),
					mCalendar.get(Calendar.MINUTE), false);

		case DATE_DIALOG_ID:
			return new DatePickerDialog(this, mDateSetListener,
					mCalendar.get(Calendar.YEAR),
					mCalendar.get(Calendar.MONTH),
					mCalendar.get(Calendar.DAY_OF_MONTH));

		case DIALOG_SHOW_MESSAGE:
			AlertDialog.Builder messageBuilder = new AlertDialog.Builder(this);
			messageBuilder.setMessage(mErrorMessage).setPositiveButton(
					getString(R.string.ok),
					new DialogInterface.OnClickListener() {
						public void onClick(DialogInterface dialog, int id) {
							dialog.cancel();
						}
					});

			AlertDialog showDialog = messageBuilder.create();
			showDialog.show();
			break;

		case DIALOG_SHOW_REQUIRED:
			AlertDialog.Builder requiredBuilder = new AlertDialog.Builder(this);
			requiredBuilder.setTitle(R.string.required_fields);
			requiredBuilder.setMessage(mErrorMessage).setPositiveButton(
					getString(R.string.ok),
					new DialogInterface.OnClickListener() {
						public void onClick(DialogInterface dialog, int id) {
							dialog.cancel();
						}
					});

			AlertDialog showRequiredDialog = requiredBuilder.create();
			showRequiredDialog.show();
			break;

		//prompt for unsaved changes
		case DIALOG_SHOW_PROMPT: {
			AlertDialog dialog = (new AlertDialog.Builder(this)).create();
			dialog.setTitle(getString(R.string.unsaved_changes));
			dialog.setMessage(getString(R.string.want_to_cancel));
			dialog.setButton(getString(R.string.no),
					new Dialog.OnClickListener() {
						public void onClick(DialogInterface dialog, int which) {

							dialog.dismiss();
						}
					});
			dialog.setButton2(getString(R.string.yes),
					new Dialog.OnClickListener() {
						public void onClick(DialogInterface dialog, int which) {
							finish();
							dialog.dismiss();
						}
					});

			dialog.setCancelable(false);
			return dialog;
		}
		
		//prompt for report deletion
		case DIALOG_SHOW_DELETE_PROMPT: {
			AlertDialog dialog = (new AlertDialog.Builder(this)).create();
			dialog.setTitle(getString(R.string.delete_report));
			dialog.setMessage(getString(R.string.want_to_delete));
			dialog.setButton(getString(R.string.no),
					new Dialog.OnClickListener() {
						public void onClick(DialogInterface dialog, int which) {

							dialog.dismiss();
						}
					});
			dialog.setButton2(getString(R.string.yes),
					new Dialog.OnClickListener() {
						public void onClick(DialogInterface dialog, int which) {
							//delete report
							deleteReport();
							dialog.dismiss();
						}
					});

			dialog.setCancelable(false);
			return dialog;
		}

		}
		return null;
	}

	@Override
	protected void onPrepareDialog(int id, Dialog dialog) {
		switch (id) {
		case TIME_DIALOG_ID:
			((TimePickerDialog) dialog).updateTime(
					mCalendar.get(Calendar.HOUR_OF_DAY),
					mCalendar.get(Calendar.MINUTE));
			break;
		case DATE_DIALOG_ID:
			((DatePickerDialog) dialog).updateDate(
					mCalendar.get(Calendar.YEAR),
					mCalendar.get(Calendar.MONTH),
					mCalendar.get(Calendar.DAY_OF_MONTH));
			break;

		case DIALOG_MULTIPLE_CATEGORY:
			final AlertDialog alert = (AlertDialog) dialog;
			final ListView list = alert.getListView();
			// been
			// selected, then uncheck
			// selected categories
			if (mVectorCategories.size() > 0) {
				for (String s : mVectorCategories) {
					try {
						// @inoran fix
						list.setItemChecked(
								mCategoryLength - Integer.parseInt(s), true);
					} catch (NumberFormatException e) {
						log("NumberFormatException", e);
					}
				}
			} else {
				list.clearChoices();
			}

			break;

		}
	}

	// fetch categories
	public String[] showCategories() {
		ListReportModel mListReportModel = new ListReportModel();
		List<Category> listCategories = mListReportModel.getCategories(this);
		if (listCategories != null && listCategories.size() > 0) {
			int categoryCount = listCategories.size();
			int categoryAmount = 0;
			if (categoryCount > 0) {
				categoryAmount = categoryCount;
			} else {
				mCategoriesId.clear();
				mCategoriesTitle.clear();
				categoryAmount = 1;
			}

			String categories[] = new String[categoryAmount];
			mCategoryLength = categories.length;

			int i = 0;
			for (Category category : mListReportModel.getCategories(this)) {
				categories[i] = category.getCategoryTitle();
				mCategoriesTitle.put(String.valueOf(category.getCategoryId()),
						category.getCategoryTitle());
				mCategoriesId.add(String.valueOf(category.getCategoryId()));
				i++;
			}
			return categories;
		}
		return null;
	}

	private void updateDisplay() {
		Date date = mCalendar.getTime();
		if (date != null) {
			SimpleDateFormat dateFormat = new SimpleDateFormat("MMMM dd, yyyy");
			view.mPickDate.setText(dateFormat.format(date));

			SimpleDateFormat timeFormat = new SimpleDateFormat("h:mm a");
			view.mPickTime.setText(timeFormat.format(date));

			// Because the API doesn't support dates in diff Locale mode, force
			// it to show time in US
			SimpleDateFormat submitFormat = new SimpleDateFormat(
					"yyy-MM-dd kk:mm:ss", Locale.US);
			mDateToSubmit = submitFormat.format(date);
		} else {
			view.mPickDate.setText(R.string.incident_date);
			view.mPickTime.setText(R.string.incident_time);
			mDateToSubmit = null;
		}
	}

	private void setDateAndTime(String dateTime) {
		if (dateTime != null && !(TextUtils.isEmpty(dateTime))) {
			SimpleDateFormat dateFormat = new SimpleDateFormat("MMMM dd, yyyy");
			Date date;
			try {
				date = dateFormat.parse(dateTime);

				if (date != null) {

					view.mPickDate.setText(dateFormat.format(date));

					SimpleDateFormat timeFormat = new SimpleDateFormat("h:mm a");
					view.mPickTime.setText(timeFormat.format(date));

					// Because the API doesn't support dates in diff Locale
					// mode,
					// force
					// it to show time in US
					SimpleDateFormat submitFormat = new SimpleDateFormat(
							"yyy-MM-dd kk:mm:ss", Locale.US);
					mDateToSubmit = submitFormat.format(date);
				} else {
					view.mPickDate.setText(R.string.incident_date);
					view.mPickTime.setText(R.string.incident_time);
					mDateToSubmit = null;
				}

			} catch (ParseException e) {
				log(e.getMessage());

			}
		}
	}

	private DatePickerDialog.OnDateSetListener mDateSetListener = new DatePickerDialog.OnDateSetListener() {
		public void onDateSet(DatePicker view, int year, int monthOfYear,
				int dayOfMonth) {
			mCalendar.set(year, monthOfYear, dayOfMonth);
			updateDisplay();
		}
	};

	private TimePickerDialog.OnTimeSetListener mTimeSetListener = new TimePickerDialog.OnTimeSetListener() {
		public void onTimeSet(TimePicker view, int hourOfDay, int minute) {
			mCalendar.set(Calendar.HOUR_OF_DAY, hourOfDay);
			mCalendar.set(Calendar.MINUTE, minute);
			updateDisplay();
		}
	};

	/**
	 * Sets nVectorCategories
	 * 
	 * @param aVectorCategories
	 *            categories
	 */
	public void setVectorCategories(Vector<String> aVectorCategories) {
		mVectorCategories = aVectorCategories;
	}

	/**
	 * Sets the selected categories for submission
	 * 
	 * @param aSelectedCategories
	 */
	private void setSelectedCategories(Vector<String> aSelectedCategories) {
		// clear
		view.mBtnAddCategory.setText(R.string.incident_add_category);
		if (aSelectedCategories.size() > 0) {
			StringBuilder categories = new StringBuilder();
			for (String category : aSelectedCategories) {
				if (categories.length() > 0) {
					categories.append(", ");
				}
				if (!TextUtils.isEmpty(category)) {
					categories.append(mCategoriesTitle.get(category));
				}
			}

			if (!TextUtils.isEmpty(categories.toString())) {
				view.mBtnAddCategory.setText(categories.toString());
			} else {

				view.mBtnAddCategory.setText(R.string.incident_add_category);
			}
		}
	}

	/**
	 * Get the selected categories as a csv
	 * 
	 * @param aSelectedCategories
	 */
	private String getSelectedCategories() {
		if (mVectorCategories != null) {
			if (mVectorCategories.size() > 0) {
				StringBuilder categories = new StringBuilder();
				for (String catetory : mVectorCategories) {
					if (categories.length() > 0) {
						categories.append(", ");
					}
					categories.append(catetory);
				}
				return categories.toString();
			}
		}
		return null;
	}

	/**
	 * Get shared text from other Android applications
	 */
	public void getSharedText() {
		Intent intent = getIntent();
		String action = intent.getAction();
		if (action != null) {
			if (action.equals(Intent.ACTION_SEND)
					|| action.equals(Intent.ACTION_CHOOSER)) {
				CharSequence text = intent
						.getCharSequenceExtra(Intent.EXTRA_TEXT);
				if (text != null) {
					view.mIncidentDesc.setText(text);
				}
			}
		}
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		super.onActivityResult(requestCode, resultCode, data);
		if (resultCode == RESULT_OK) {
			if (requestCode == REQUEST_CODE_CAMERA) {

				Uri uri = PhotoUtils.getPhotoUri(photoName, this);
				Bitmap bitmap = PhotoUtils.getCameraPhoto(this, uri);
				PhotoUtils.savePhoto(this, bitmap, photoName);
				log(String.format("REQUEST_CODE_CAMERA %dx%d",
						bitmap.getWidth(), bitmap.getHeight()));

			} else if (requestCode == REQUEST_CODE_IMAGE) {
				Bitmap bitmap = PhotoUtils
						.getGalleryPhoto(this, data.getData());
				PhotoUtils.savePhoto(this, bitmap, photoName);
				log(String.format("REQUEST_CODE_IMAGE %dx%d",
						bitmap.getWidth(), bitmap.getHeight()));
			}

			pendingPhoto.refresh();
		}
	}

	@Override
	protected void locationChanged(double latitude, double longitude) {
		updateMarker(latitude, longitude, true);
		if (!view.mLatitude.hasFocus() && !view.mLongitude.hasFocus()) {
			view.mLatitude.setText(String.valueOf(latitude));
			view.mLongitude.setText(String.valueOf(longitude));
		}
		if (reverseGeocoderTask == null || !reverseGeocoderTask.isExecuting()) {
			reverseGeocoderTask = new ReverseGeocoderTask(this);
			reverseGeocoderTask.execute(latitude, longitude);
		}

	}

	/**
	 * Asynchronous Reverse Geocoder Task
	 */
	private class ReverseGeocoderTask extends GeocoderTask {

		public ReverseGeocoderTask(Context context) {
			super(context);
		}

		@Override
		protected void onPostExecute(String result) {
			log(getClass().getSimpleName(),
					String.format("onPostExecute %s", result));
			if (TextUtils.isEmpty(view.mIncidentLocation.getText().toString()))
				view.mIncidentLocation.setText(result);
			executing = false;
		}
	}

	private TextWatcher latLonTextWatcher = new TextWatcher() {
		public void afterTextChanged(Editable s) {
		}

		public void beforeTextChanged(CharSequence s, int start, int count,
				int after) {
		}

		public void onTextChanged(CharSequence s, int start, int before,
				int count) {
			try {
				if (view.mLatitude.hasFocus() || view.mLongitude.hasFocus()) {
					locationChanged(Double.parseDouble(view.mLatitude.getText()
							.toString()), Double.parseDouble(view.mLongitude
							.getText().toString()));
				}
			} catch (Exception ex) {
				log("TextWatcher", ex);
			}
		}
	};

	/**
	 * Go to reports screen
	 */
	public void goToReports() {
		Intent intent = new Intent(AddReportActivity.this,
				ReportTabActivity.class);
		startActivityForResult(intent, 0);
		setResult(RESULT_OK);
	}

	public void onLocationChanged(Location arg0) {
	}

	public void onProviderDisabled(String provider) {
	}

	public void onProviderEnabled(String provider) {
	}

	public void onStatusChanged(String provider, int status, Bundle extras) {
	}

	@Override
	protected boolean onSaveChanges() {
		return addReport();
	}

	@Override
	protected boolean isRouteDisplayed() {
		return false;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see android.widget.ViewSwitcher.ViewFactory#makeView()
	 */
	@Override
	public View makeView() {
		ImageView i = new ImageView(this);
		i.setAdjustViewBounds(true);
		i.setScaleType(ImageView.ScaleType.FIT_CENTER);
		i.setLayoutParams(new ImageSwitcher.LayoutParams(
				android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
				android.view.ViewGroup.LayoutParams.WRAP_CONTENT));

		return i;
	}

	/**
	 * (non-Javadoc)
	 * 
	 * @see android.widget.AdapterView.OnItemClickListener#onItemClick(android.widget
	 *      .AdapterView, android.view.View, int, long)
	 */
	@Override
	public void onItemClick(AdapterView<?> parent, View view, int position,
			long id) {
		this.view.mSwitcher.setImageDrawable(ImageManager.getPendingDrawables(
				this, pendingPhoto.getItem(position).getPhoto(),
				Util.getScreenWidth(this)));

	}

}
