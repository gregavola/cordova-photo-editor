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

import ly.img.android.PESDK;
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
import ly.img.android.ui.activities.ImgLyIntent;
import ly.img.android.ui.activities.PhotoEditorBuilder;
import  ly.img.android.sdk.decoder.ImageSource;
import ly.img.android.sdk.models.config.CropAspectConfig;

public class PESDKPlugin extends CordovaPlugin {

    public static final int PESDK_EDITOR_RESULT = 1;
    public final String editParam = "false";
    public static boolean shouldSave = false;
    public static boolean shouldUseEditor = false;
    public static JSONArray customStickers = null;
    private static boolean didInitializeSDK = false;
    public SettingsList settingsList = new SettingsList();
    private CallbackContext callback = null;

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
            JSONObject options = data.getJSONObject(0);
            String filepath = options.optString("path", "");
            customStickers = options.getJSONArray("stickers");
            shouldSave = options.optBoolean("shouldSave", false);
            shouldUseEditor = options.optBoolean("shouldUseEditor", false);

            Log.e("PHOTO_EDITOR", String.valueOf(shouldSave));
            Log.e("PHOTO_EDITOR", filepath);
            Log.e("PHOTO_EDITOR", customStickers.toString());
            Log.e("PHOTO_EDITOR", String.valueOf(customStickers.length()));

            Activity activity = this.cordova.getActivity();
            activity.runOnUiThread(this.present(activity, filepath, callbackContext));
            return true;
        } else {
            return false;
        }
    }

    private Runnable present(final Activity mainActivity, final String filepath, final CallbackContext callbackContext) {
        callback = callbackContext;
        final PESDKPlugin self = this;
        return new Runnable() {
            public void run() {
                if (mainActivity != null && filepath.length() > 0) {

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

                    settingsList
                        .getSettingsModel(EditorLoadSettings.class)
                        .setImageSourcePath(filepath.replace("file://", ""), true) // Load with delete protection true!
                        .getSettingsModel(EditorSaveSettings.class)
                        .setExportDir(Directory.DCIM, "untappd")
                        .setExportPrefix("result_")
                        .setJpegQuality(80, false)
                        .setSavePolicy(
                            EditorSaveSettings.SavePolicy.KEEP_SOURCE_AND_CREATE_OUTPUT_IF_NECESSARY
                        );

                    cordova.setActivityResultCallback(self);
                    new PhotoEditorBuilder(mainActivity)
                            .setSettingsList(settingsList)
                            .startActivityForResult(mainActivity, PESDK_EDITOR_RESULT);
                } else {
                    // Just open the camera
                    Intent intent = new Intent(mainActivity, CameraActivity.class);
                    callback = callbackContext;
                    cordova.startActivityForResult(self, intent, PESDK_EDITOR_RESULT);
                }
            }
        };
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, android.content.Intent data) {
        Log.e("PHOTO-EDITOR-P", String.valueOf(resultCode));
        if (requestCode == PESDK_EDITOR_RESULT) {
            switch (resultCode){
                case Activity.RESULT_OK:
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

    private void success(Intent data) {
        String path = data.getStringExtra(ImgLyIntent.RESULT_IMAGE_PATH);

        if (shouldSave) {
            File mMediaFolder = new File(path);
            MediaScannerConnection.scanFile(cordova.getActivity().getApplicationContext(),
                    new String[]{mMediaFolder.getAbsolutePath()},
                    null,
                    new MediaScannerConnection.OnScanCompletedListener() {
                        public void onScanCompleted(String path, Uri uri) {
                            if (uri == null) {
                                callback.error("Media saving failed.");
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
                                    callback.error(e.getMessage());
                                }
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
                callback.error(e.getMessage());
            }
        }
    }

}
