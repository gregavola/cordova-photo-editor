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
import ly.img.android.sdk.models.state.PESDKConfig;
import ly.img.android.sdk.models.constant.Directory;
import ly.img.android.sdk.models.config.StickerCategoryConfig;
import ly.img.android.sdk.models.config.ImageStickerConfig;
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
    public static boolean shouldSave = false;
    public static JSONArray customStickers = null;
    public static String categoryName = "Untappd Stickers";
    public static String categoryImage = null;
    private static boolean didInitializeSDK = false;
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
            categoryName = options.optString("categoryName", "Untappd Stickers");
            categoryImage = options.optString("categoryImage", null);
            shouldSave = options.optBoolean("shouldSave", false);

            Log.e("PHOTO_EDITOR", String.valueOf(shouldSave));
            Log.e("PHOTO_EDITOR", filepath);
            Log.e("PHOTO_EDITOR", categoryName);
            Log.e("PHOTO_EDITOR", categoryImage);
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
                    SettingsList settingsList = new SettingsList();

                    if (customStickers.length() != 0 && categoryImage != null) {
                        PESDKConfig config = settingsList.getConfig();

                        Log.e("PHOTOEDITOR", "true");

                        ArrayList<StickerConfigInterface> customStickersList = new ArrayList<StickerConfigInterface>();

                        for (int i=0; i<customStickers.length(); i++) {
                            try {
                                JSONObject item = customStickers.getJSONObject(i);

                                String itemUrl = item.getString("image_url");
                                String itemThumbUrl = item.getString("image_thumb_url");

                                ImageStickerConfig configItem = new ImageStickerConfig(
                                        "unique-photo-id-"+i,
                                        "Photo Name - " +i,
                                        ImageSource.create(Uri.parse(itemThumbUrl)),
                                        ImageSource.create(Uri.parse(itemUrl))

                                );

                                customStickersList.add(configItem);

                            } catch (JSONException e) {
                                e.printStackTrace();
                            }
                        }

                        StickerCategoryConfig stickerCategoryConfig = new StickerCategoryConfig(
                                "Untappd Stickers",
                                ImageSource.create(Uri.parse(categoryImage)),
                                customStickersList
                        );

                        config.setStickerLists(stickerCategoryConfig);
                    }

                    settingsList
                        .getSettingsModel(EditorLoadSettings.class)
                        .setImageSourcePath(filepath.replace("file://", ""), true) // Load with delete protection true!
                        .getSettingsModel(EditorSaveSettings.class)
                        .setExportDir(Directory.DCIM, "test")
                        .setExportPrefix("result_")
                        .setJpegQuality(80, false)
                        .setSavePolicy(
                            EditorSaveSettings.SavePolicy.KEEP_SOURCE_AND_CREATE_OUTPUT_IF_NECESSARY
                        );

                    cordova.setActivityResultCallback(self);
                    new PhotoEditorBuilder(mainActivity)
                            .setSettingsList(settingsList)
                            .startActivityForResult(mainActivity, PESDK_EDITOR_RESULT);
                }
            }
        };
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, android.content.Intent data) {
        if (requestCode == PESDK_EDITOR_RESULT) {
            switch (resultCode){
                case Activity.RESULT_OK:
                    success(data);
                    break;
                case Activity.RESULT_CANCELED:
                    callback.error(""); // empty string signals cancellation
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
                callback.success(json);
            } catch (Exception e) {
                callback.error(e.getMessage());
            }
        }
    }

}
