package com.photoeditorsdk.cordova;

import android.app.Activity;
import android.content.Intent;
import android.media.MediaScannerConnection;
import android.net.Uri;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaInterface;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.CordovaWebView;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import android.util.Log;

import android.os.Environment;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

import android.provider.MediaStore;

import ly.img.android.PESDK;
import ly.img.android.sdk.models.state.CameraSettings;
import ly.img.android.sdk.utils.DataSourceArrayList;
import ly.img.android.sdk.models.state.PESDKConfig;
import ly.img.android.sdk.models.constant.Directory;
import ly.img.android.sdk.models.config.StickerCategoryConfig;
import ly.img.android.sdk.models.config.ImageStickerConfig;
import ly.img.android.sdk.models.config.interfaces.StickerListConfigInterface;
import ly.img.android.sdk.models.config.interfaces.StickerConfigInterface;
import ly.img.android.sdk.models.state.EditorLoadSettings;
import ly.img.android.sdk.models.state.EditorSaveSettings;
import ly.img.android.sdk.models.state.manager.SettingsList;
import ly.img.android.ui.activities.CameraPreviewBuilder;
import ly.img.android.ui.activities.ImgLyIntent;
import ly.img.android.ui.activities.PhotoEditorBuilder;
import  ly.img.android.sdk.decoder.ImageSource;
import ly.img.android.sdk.models.config.CropAspectConfig;

public class PESDKPlugin extends CordovaPlugin {

    public static final int PESDK_EDITOR_RESULT = 1;
    public static final int CAMERA_PREVIEW_RESULT = 2;
    public static final String HIDDENFOLDER = "untappdHidden";
    public static String HIDDENFOLDERPATH = "";
    public static final String FOLDER = "untappd";
    public final String editParam = "false";
    public static boolean isCameraOnly = false;
    public static boolean shouldSave = false;
    public static boolean shouldSaveCamera = false;
    public static boolean shouldUseEditor = false;
    public static JSONArray customStickers = null;
    private static boolean didInitializeSDK = false;
    public SettingsList settingsList = new SettingsList();
    public CallbackContext callback = null;

    @Override
    public void initialize(CordovaInterface cordova, CordovaWebView webView) {
        super.initialize(cordova, webView);

        if (!didInitializeSDK) {
            PESDK.init(cordova.getActivity().getApplication(), "android_license");
            didInitializeSDK = true;
        }
    }

    @Override
    public boolean execute(String action, JSONArray data, CallbackContext callbackContext) throws JSONException {
        if (action.equals("present")) {
            // Extract image path
            callback = callbackContext;
            JSONObject options = data.getJSONObject(0);
            String filepath = options.optString("path", "");
            customStickers = options.getJSONArray("stickers");
            shouldSave = options.optBoolean("shouldSave", false);
            shouldSaveCamera = options.optBoolean("shouldSaveCamera", false);
            shouldUseEditor = options.optBoolean("shouldUseEditor", false);

            Log.e("PHOTO_EDITOR", String.valueOf(shouldSave));
            Log.e("PHOTO_EDITOR", filepath);
            Log.e("PHOTO_EDITOR", customStickers.toString());
            Log.e("PHOTO_EDITOR", String.valueOf(customStickers.length()));

            setupStickers();

            if (!shouldSave || !shouldSaveCamera) {
                setupFolders();
            } else {
                Log.e("PHOTO_EDITOR", "NOT SETTING UP FOLDERS");
            }


            Activity activity = this.cordova.getActivity();
            activity.runOnUiThread(this.present(activity, filepath, callbackContext));
            return true;
        } else if (action.equals("delete")) {
            Log.e("PHOTO_EDITOR","FILE PATH IS ABOUT TO BE DELETED");
            String filePath = Environment.getExternalStorageDirectory() + HIDDENFOLDER;
            Log.e("PHOTO_EDITOR",filePath);
            File f = new File(filePath);
            deleteRecursive(f);

            callback.success("true");

            return true;
        } else {
            return false;
        }
    }

    public void setupFolders() {


        String filePath = Environment.getExternalStorageDirectory()+ "/" + HIDDENFOLDER;
        File f = new File(filePath);

        if (!f.exists()) {
            f.mkdirs();
        }

        File gpxfile = new File(f, ".nomedia");

        HIDDENFOLDERPATH = filePath;

        Log.e("PHOTO_EDITOR", "FOLDER CREATED " + HIDDENFOLDERPATH);
        Log.e("PHOTO_EDITOR", "FILE CREATED " + gpxfile.getAbsolutePath());
    }

    private Runnable present(final Activity mainActivity, final String filepath, final CallbackContext callbackContext) {
        final PESDKPlugin self = this;
        return new Runnable() {
            public void run() {

                callback = callbackContext;

                Log.e("PHOTO_EDITOR", "SHOULD SAVE CAMERA: " + String.valueOf(shouldSaveCamera));
                Log.e("PHOTO_EDITOR", "SHOULD SAVE EDITOR: " + String.valueOf(shouldSave));

                if (filepath.length() > 0) {

                    settingsList
                            .getSettingsModel(EditorLoadSettings.class)
                            .setImageSourcePath(filepath.replace("file://", ""), true) // Load with delete protection true!
                            .getSettingsModel(EditorSaveSettings.class)
                            .setExportPrefix("result_")
                            .setSavePolicy(
                                    EditorSaveSettings.SavePolicy.RETURN_ALWAYS_ONLY_OUTPUT
                            );


                    if (!shouldSave) {
                        settingsList.getSettingsModel(EditorSaveSettings.class).setExportDir(HIDDENFOLDERPATH);
                    } else {
                        settingsList.getSettingsModel(EditorSaveSettings.class).setExportDir(Directory.DCIM, FOLDER);
                    }

                    isCameraOnly = false;

                    cordova.setActivityResultCallback(self);
                    new PhotoEditorBuilder(mainActivity)
                            .setSettingsList(settingsList)
                            .startActivityForResult(mainActivity, CAMERA_PREVIEW_RESULT);


                } else {

                    isCameraOnly = true;

                    settingsList
                            // Set custom camera export settings
                            .getSettingsModel(CameraSettings.class)
                            .setExportPrefix("camera_")
                            // Set custom editor export settings
                            .getSettingsModel(EditorSaveSettings.class)
                            .setExportPrefix("result_")
                            .setSavePolicy(
                                    EditorSaveSettings.SavePolicy.KEEP_SOURCE_AND_CREATE_OUTPUT_IF_NECESSARY
                            );

                    if (!shouldSaveCamera) {
                        settingsList.getSettingsModel(CameraSettings.class).setExportDir(HIDDENFOLDERPATH);
                    } else {
                        settingsList.getSettingsModel(CameraSettings.class).setExportDir(Directory.DCIM, FOLDER);
                    }

                    if (!shouldSave) {
                        settingsList.getSettingsModel(EditorSaveSettings.class).setExportDir(HIDDENFOLDERPATH);
                    } else {
                        settingsList.getSettingsModel(EditorSaveSettings.class).setExportDir(Directory.DCIM, FOLDER);
                    }

                    cordova.setActivityResultCallback(self);
                    new CameraPreviewBuilder(mainActivity)
                            .setSettingsList(settingsList)
                            .startActivityForResult(mainActivity, CAMERA_PREVIEW_RESULT);
                }
            }
        };
    }

    public void resetCamara() {
        Activity activity = this.cordova.getActivity();
        activity.runOnUiThread(this.present(activity, "", callback));
    }

    public void setupStickers() {
        if (customStickers.length() != 0) {
            PESDKConfig config = settingsList.getConfig();

            Log.e("PHOTOEDITOR", "true");

            // Reference to the Category List
            DataSourceArrayList<StickerListConfigInterface> stickerCategories = config.getStickerConfig();
            // Clear the Category List, to remove default assets
            stickerCategories.clear();

            for (int i=0; i<customStickers.length(); i++) {
                try {
                    JSONObject categoryItemData = customStickers.getJSONObject(i);
                    JSONObject stickerList = categoryItemData.getJSONObject("stickers");

                    ArrayList<StickerConfigInterface> customStickersList = new ArrayList();

                    if (stickerList != null) {

                        JSONArray stickerListItems = stickerList.getJSONArray("items");

                        Log.e("PHOTO_EDITOR", stickerListItems.toString());

                        for (int q=0; q<stickerListItems.length(); q++) {
                            JSONObject item = stickerListItems.getJSONObject(q);

                            String itemThumbUrl = item.getString("image_thumb_url");
                            String itemUrl = item.getString("image_url");

                            customStickersList.add(new ImageStickerConfig(
                                    "unique-photo-id-" + i + "-" +q,
                                    "Photo Name - " + q,
                                    ImageSource.create(Uri.parse(itemThumbUrl)),
                                    ImageSource.create(Uri.parse(itemUrl))

                            ));
                        }

                    }

                    //Add new content to the ArrayList
                    stickerCategories.add(new StickerCategoryConfig(
                            categoryItemData.getString("pack_title"),
                            ImageSource.create(Uri.parse(categoryItemData.getString("pack_image_url"))),
                            customStickersList
                    ));


                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
        }

    }

    public boolean getCameraSave() {
        return shouldSaveCamera;
    }

    public JSONArray getStickersArray() {
        return customStickers;
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, android.content.Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        Log.e("PHOTO-EDITOR-ON-ACTIVITY", String.valueOf(resultCode));
        if (resultCode == Activity.RESULT_OK && requestCode == CAMERA_PREVIEW_RESULT) {
            Log.e("PHOTO_EDITOR","RESULT OK CAMERA_PREVIEW_RESULT");
            if (data != null) {
                success(data);
            } else {
                callback.error("no image selected"); // empty string signals cancellation
            }

        }
       else if (resultCode == Activity.RESULT_CANCELED && requestCode == CAMERA_PREVIEW_RESULT) {
            Log.e("PHOTO_EDITOR", "CANCELED");
            if (data != null) {
                if (isCameraOnly) {
                    Log.e("PHOTO_EDITOR", "CAMERA DETECTED RESETTING");
                    this.resetCamara();
                } else {
                    callback.error("no image selected"); // empty string signals cancellation
                }
            } else {
                callback.error("no image selected"); // empty string signals cancellation
            }
        }
        else if (requestCode == PESDK_EDITOR_RESULT) {
            switch (resultCode){
                case Activity.RESULT_OK:
                    String fromCameraTest = data.getStringExtra("fromCamera");
                    Log.e("PHOTO_EDITOR","FromCameraSuccess: " + fromCameraTest);
                    if (fromCameraTest != null) {
                        if (fromCameraTest.equals("true")) {
                            setupStickers();
                        }
                    }
                    success(data);
                    break;
                case Activity.RESULT_CANCELED:
                    if (data != null) {
                        String fromCamera = data.getStringExtra("fromCamera");
                        Log.e("PHOTO_EDITOR","FromCamera: " + fromCamera);
                        if (fromCamera != null) {
                            if (fromCamera.equals("true")) {
                                // call the camera back from the dead
                                Log.e("PHOTO_EDITOR","About to recall camera");
                                Activity activity = this.cordova.getActivity();
                                activity.runOnUiThread(this.present(activity, "", callback));
                            } else {
                                callback.error("no image selected"); // empty string signals cancellation
                            }
                        } else {
                            callback.error("no image selected"); // empty string signals cancellation
                        }
                    } else {
                        callback.error("no image selected"); // empty string signals cancellation
                    }

                    break;

                default:
                    callback.error("Media error (code " + resultCode + ")");
                    break;
            }
        }
    }

    private void deleteRecursive(File fileOrDirectory) {

        if (fileOrDirectory.isDirectory())
            for (File child : fileOrDirectory.listFiles())
                deleteRecursive(child);

        fileOrDirectory.delete();

    }

    private void success(Intent data) {
        Log.e("PHOTO_EDITOR", "DATA SUCCESS CALLED");
        //String path = data.getStringExtra(ImgLyIntent.RESULT_IMAGE_PATH);

        Uri resultURI = data.getParcelableExtra(ImgLyIntent.RESULT_IMAGE_URI);
        Log.e("PHOTO_EDITOR","resultURI");
        Log.e("PHOTO_EDITOR",resultURI.toString());

        Uri sourceURI = data.getParcelableExtra(ImgLyIntent.SOURCE_IMAGE_URI);
        Log.e("PHOTO_EDITOR","sourceURI");
        Log.e("PHOTO_EDITOR",sourceURI.toString());

        if (isCameraOnly) {
            // resultURI = savedImageFromEditor
            // sourceURI = savedImagefromCamera

            if (shouldSaveCamera) {
                Log.e("PHOTO_EDITOR","I AM GOING TO SAVE SOURCE");
                if (sourceURI != null) {
                    this.cordova.getActivity().getApplicationContext().sendBroadcast(new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE).setData(sourceURI));
                }
            }

            if (shouldSave) {
                Log.e("PHOTO_EDITOR","I AM GOING TO SAVE RESULT");
                if (resultURI != null) {
                    this.cordova.getActivity().getApplicationContext().sendBroadcast(new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE).setData(resultURI));
                }
            }

        } else {
            if (shouldSave) {
                Log.e("PHOTO_EDITOR","I AM GOING TO SAVE RESULT");
                if (resultURI != null) {
                    this.cordova.getActivity().getApplicationContext().sendBroadcast(new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE).setData(resultURI));
                }
            }
        }

        try {
            JSONObject json = new JSONObject();
            json.put("url", resultURI);
            json.put("source_url", sourceURI);

            if (settingsList.getSettingsModel(EditorSaveSettings.class).isExportNecessary()) {
                json.put("edit", "true");
            } else {
                json.put("edit", "false");
            }

            Log.e("PHOTO_EDITOR", json.toString());

            callback.success(json);
        } catch (Exception e) {
            callback.error("With saving photo: " + e.getMessage());
        }

            /*
            File mMediaFolder = new File(path);
            MediaScannerConnection.scanFile(cordova.getActivity().getApplicationContext(),
                    new String[]{mMediaFolder.getAbsolutePath()},
                    null,
                    new MediaScannerConnection.OnScanCompletedListener() {
                        public void onScanCompleted(String path, Uri uri) {
                            try {

                                Log.e("PHOTO_EDITOR", "path: " + path.toString());
                                Log.e("PHOTO_EDITOR", "uri: " + uri.toString());

                                JSONObject json = new JSONObject();
                                json.put("url", Uri.fromFile(new File(path)));

                                if (settingsList.getSettingsModel(EditorSaveSettings.class).isExportNecessary()) {
                                    json.put("edit", "true");
                                } else {
                                    json.put("edit", "false");
                                }

                                callback.success(json);
                            } catch (Exception e) {
                                callback.error("With scanning photo: " +e.getMessage());
                            }
                        }
                    }
            );
        } else {
            try {
                JSONObject json = new JSONObject();
                json.put("url", Uri.fromFile(new File(path)));

                if (settingsList.getSettingsModel(EditorSaveSettings.class).isExportNecessary()) {
                    json.put("edit", "true");
                } else {
                    json.put("edit", "false");
                }

                callback.success(json);
            } catch (Exception e) {
                callback.error("With saving photo: " + e.getMessage());
            }
        }*/
    }

}
