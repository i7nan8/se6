package org.apache.cordova.camera;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.ExifInterface;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Base64;
import androidx.core.content.FileProvider;
import com.trusteer.tas.TasDefs;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import org.apache.cordova.BuildHelper;
import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.LOG;
import org.apache.cordova.PermissionHelper;
import org.apache.cordova.PluginResult;
import org.azeckoski.reflectutils.transcoders.JSONTranscoder;
import org.azeckoski.reflectutils.transcoders.Transcoder;
import org.json.JSONArray;
import org.json.JSONException;

public class CameraLauncher extends CordovaPlugin implements MediaScannerConnection.MediaScannerConnectionClient {
    private static final int ALLMEDIA = 2;
    private static final int CAMERA = 1;
    private static final String CROPPED_URI_KEY = "croppedUri";
    private static final int CROP_CAMERA = 100;
    private static final int DATA_URL = 0;
    private static final int FILE_URI = 1;
    private static final String GET_All = "Get All";
    private static final String GET_PICTURE = "Get Picture";
    private static final String GET_VIDEO = "Get Video";
    private static final String IMAGE_URI_KEY = "imageUri";
    private static final int JPEG = 0;
    private static final String JPEG_EXTENSION = ".jpg";
    private static final String JPEG_MIME_TYPE = "image/jpeg";
    private static final String JPEG_TYPE = "jpg";
    private static final String LOG_TAG = "CameraLauncher";
    private static final int NATIVE_URI = 2;
    public static final int PERMISSION_DENIED_ERROR = 20;
    private static final int PHOTOLIBRARY = 0;
    private static final int PICTURE = 0;
    private static final int PNG = 1;
    private static final String PNG_EXTENSION = ".png";
    private static final String PNG_MIME_TYPE = "image/png";
    private static final String PNG_TYPE = "png";
    private static final int SAVEDPHOTOALBUM = 2;
    public static final int SAVE_TO_ALBUM_SEC = 1;
    private static final String TAKE_PICTURE_ACTION = "takePicture";
    public static final int TAKE_PIC_SEC = 0;
    private static final String TIME_FORMAT = "yyyyMMdd_HHmmss";
    private static final int VIDEO = 1;
    protected static final String[] permissions = {"android.permission.CAMERA", "android.permission.READ_EXTERNAL_STORAGE", "android.permission.WRITE_EXTERNAL_STORAGE"};
    private boolean allowEdit;
    private String applicationId;
    public CallbackContext callbackContext;
    private MediaScannerConnection conn;
    private boolean correctOrientation;
    private Uri croppedUri;
    private int destType;
    private int encodingType;
    private ExifHelper exifData;
    private CordovaUri imageUri;
    private int mQuality;
    private int mediaType;
    private int numPics;
    private boolean orientationCorrected;
    private boolean saveToPhotoAlbum;
    private Uri scanMe;
    private int srcType;
    private int targetHeight;
    private int targetWidth;

    private int exifToDegrees(int i) {
        if (i == 6) {
            return 90;
        }
        if (i == 3) {
            return 180;
        }
        return i == 8 ? 270 : 0;
    }

    private String getMimetypeForFormat(int i) {
        return i == 1 ? PNG_MIME_TYPE : i == 0 ? JPEG_MIME_TYPE : "";
    }

    @Override // org.apache.cordova.CordovaPlugin
    public boolean execute(String str, JSONArray jSONArray, CallbackContext callbackContext2) throws JSONException {
        this.callbackContext = callbackContext2;
        this.applicationId = (String) BuildHelper.getBuildConfigValue(this.f30cordova.getActivity(), "APPLICATION_ID");
        this.applicationId = this.preferences.getString("applicationId", this.applicationId);
        if (!str.equals(TAKE_PICTURE_ACTION)) {
            return false;
        }
        this.srcType = 1;
        this.destType = 1;
        this.saveToPhotoAlbum = false;
        this.targetHeight = 0;
        this.targetWidth = 0;
        this.encodingType = 0;
        this.mediaType = 0;
        this.mQuality = 50;
        this.destType = jSONArray.getInt(1);
        this.srcType = jSONArray.getInt(2);
        this.mQuality = jSONArray.getInt(0);
        this.targetWidth = jSONArray.getInt(3);
        this.targetHeight = jSONArray.getInt(4);
        this.encodingType = jSONArray.getInt(5);
        this.mediaType = jSONArray.getInt(6);
        this.allowEdit = jSONArray.getBoolean(7);
        this.correctOrientation = jSONArray.getBoolean(8);
        this.saveToPhotoAlbum = jSONArray.getBoolean(9);
        if (this.targetWidth < 1) {
            this.targetWidth = -1;
        }
        if (this.targetHeight < 1) {
            this.targetHeight = -1;
        }
        if (this.targetHeight == -1 && this.targetWidth == -1 && this.mQuality == 100 && !this.correctOrientation && this.encodingType == 1 && this.srcType == 1) {
            this.encodingType = 0;
        }
        try {
            if (this.srcType == 1) {
                callTakePicture(this.destType, this.encodingType);
            } else if (this.srcType == 0 || this.srcType == 2) {
                if (!PermissionHelper.hasPermission(this, "android.permission.READ_EXTERNAL_STORAGE")) {
                    PermissionHelper.requestPermission(this, 1, "android.permission.READ_EXTERNAL_STORAGE");
                } else {
                    getImage(this.srcType, this.destType, this.encodingType);
                }
            }
            PluginResult pluginResult = new PluginResult(PluginResult.Status.NO_RESULT);
            pluginResult.setKeepCallback(true);
            callbackContext2.sendPluginResult(pluginResult);
            return true;
        } catch (IllegalArgumentException unused) {
            callbackContext2.error("Illegal Argument Exception");
            callbackContext2.sendPluginResult(new PluginResult(PluginResult.Status.ERROR));
            return true;
        }
    }

    private String getTempDirectoryPath() {
        File file;
        if (Environment.getExternalStorageState().equals("mounted")) {
            file = this.f30cordova.getActivity().getExternalCacheDir();
        } else {
            file = this.f30cordova.getActivity().getCacheDir();
        }
        file.mkdirs();
        return file.getAbsolutePath();
    }

    public void callTakePicture(int i, int i2) {
        boolean z = true;
        boolean z2 = PermissionHelper.hasPermission(this, "android.permission.READ_EXTERNAL_STORAGE") && PermissionHelper.hasPermission(this, "android.permission.WRITE_EXTERNAL_STORAGE");
        boolean hasPermission = PermissionHelper.hasPermission(this, "android.permission.CAMERA");
        if (!hasPermission) {
            try {
                String[] strArr = this.f30cordova.getActivity().getPackageManager().getPackageInfo(this.f30cordova.getActivity().getPackageName(), TasDefs.ADDITIONAL_DATA_MAX_LENGTH).requestedPermissions;
                if (strArr != null) {
                    int length = strArr.length;
                    int i3 = 0;
                    while (true) {
                        if (i3 >= length) {
                            break;
                        } else if (strArr[i3].equals("android.permission.CAMERA")) {
                            z = false;
                            break;
                        } else {
                            i3++;
                        }
                    }
                }
            } catch (PackageManager.NameNotFoundException unused) {
            }
        } else {
            z = hasPermission;
        }
        if (z && z2) {
            takePicture(i, i2);
        } else if (z2 && !z) {
            PermissionHelper.requestPermission(this, 0, "android.permission.CAMERA");
        } else if (z2 || !z) {
            PermissionHelper.requestPermissions(this, 0, permissions);
        } else {
            PermissionHelper.requestPermissions(this, 0, new String[]{"android.permission.READ_EXTERNAL_STORAGE", "android.permission.WRITE_EXTERNAL_STORAGE"});
        }
    }

    public void takePicture(int i, int i2) {
        this.numPics = queryImgDB(whichContentStore()).getCount();
        Intent intent = new Intent("android.media.action.IMAGE_CAPTURE");
        File createCaptureFile = createCaptureFile(i2);
        Activity activity = this.f30cordova.getActivity();
        this.imageUri = new CordovaUri(FileProvider.getUriForFile(activity, this.applicationId + ".provider", createCaptureFile));
        intent.putExtra("output", this.imageUri.getCorrectUri());
        intent.addFlags(2);
        if (this.f30cordova == null) {
            return;
        }
        if (intent.resolveActivity(this.f30cordova.getActivity().getPackageManager()) != null) {
            this.f30cordova.startActivityForResult(this, intent, i + 32 + 1);
        } else {
            LOG.d(LOG_TAG, "Error: You don't have a default camera.  Your device may not be CTS complaint.");
        }
    }

    private File createCaptureFile(int i) {
        return createCaptureFile(i, "");
    }

    private File createCaptureFile(int i, String str) {
        String str2;
        if (str.isEmpty()) {
            str = ".Pic";
        }
        if (i == 0) {
            str2 = str + JPEG_EXTENSION;
        } else if (i == 1) {
            str2 = str + PNG_EXTENSION;
        } else {
            throw new IllegalArgumentException("Invalid Encoding Type: " + i);
        }
        return new File(getTempDirectoryPath(), str2);
    }

    public void getImage(int i, int i2, int i3) {
        int i4;
        Intent intent = new Intent();
        String str = GET_PICTURE;
        this.croppedUri = null;
        int i5 = this.mediaType;
        if (i5 == 0) {
            intent.setType("image/*");
            if (this.allowEdit) {
                intent.setAction("android.intent.action.PICK");
                intent.putExtra("crop", JSONTranscoder.BOOLEAN_TRUE);
                int i6 = this.targetWidth;
                if (i6 > 0) {
                    intent.putExtra("outputX", i6);
                }
                int i7 = this.targetHeight;
                if (i7 > 0) {
                    intent.putExtra("outputY", i7);
                }
                int i8 = this.targetHeight;
                if (i8 > 0 && (i4 = this.targetWidth) > 0 && i4 == i8) {
                    intent.putExtra("aspectX", 1);
                    intent.putExtra("aspectY", 1);
                }
                this.croppedUri = Uri.fromFile(createCaptureFile(0));
                intent.putExtra("output", this.croppedUri);
            } else {
                intent.setAction("android.intent.action.GET_CONTENT");
                intent.addCategory("android.intent.category.OPENABLE");
            }
        } else if (i5 == 1) {
            intent.setType("video/*");
            str = GET_VIDEO;
            intent.setAction("android.intent.action.GET_CONTENT");
            intent.addCategory("android.intent.category.OPENABLE");
        } else if (i5 == 2) {
            intent.setType("*/*");
            str = GET_All;
            intent.setAction("android.intent.action.GET_CONTENT");
            intent.addCategory("android.intent.category.OPENABLE");
        }
        if (this.f30cordova != null) {
            this.f30cordova.startActivityForResult(this, Intent.createChooser(intent, new String(str)), ((i + 1) * 16) + i2 + 1);
        }
    }

    private void performCrop(Uri uri, int i, Intent intent) {
        try {
            Intent intent2 = new Intent("com.android.camera.action.CROP");
            intent2.setDataAndType(uri, "image/*");
            intent2.putExtra("crop", JSONTranscoder.BOOLEAN_TRUE);
            if (this.targetWidth > 0) {
                intent2.putExtra("outputX", this.targetWidth);
            }
            if (this.targetHeight > 0) {
                intent2.putExtra("outputY", this.targetHeight);
            }
            if (this.targetHeight > 0 && this.targetWidth > 0 && this.targetWidth == this.targetHeight) {
                intent2.putExtra("aspectX", 1);
                intent2.putExtra("aspectY", 1);
            }
            int i2 = this.encodingType;
            this.croppedUri = Uri.fromFile(createCaptureFile(i2, System.currentTimeMillis() + ""));
            intent2.addFlags(1);
            intent2.addFlags(2);
            intent2.putExtra("output", this.croppedUri);
            if (this.f30cordova != null) {
                this.f30cordova.startActivityForResult(this, intent2, i + 100);
            }
        } catch (ActivityNotFoundException unused) {
            LOG.e(LOG_TAG, "Crop operation not supported on this device");
            try {
                processResultFromCamera(i, intent);
            } catch (IOException e) {
                e.printStackTrace();
                LOG.e(LOG_TAG, "Unable to write to file");
            }
        }
    }

    /* JADX WARNING: Removed duplicated region for block: B:15:0x0036  */
    /* JADX WARNING: Removed duplicated region for block: B:22:0x005c  */
    /* JADX WARNING: Removed duplicated region for block: B:25:0x0060  */
    /* JADX WARNING: Removed duplicated region for block: B:35:0x0092  */
    private void processResultFromCamera(int i, Intent intent) throws IOException {
        String str;
        int i2;
        Uri uri;
        Uri uri2;
        Uri uri3;
        Uri uri4;
        ExifHelper exifHelper = new ExifHelper();
        if (!this.allowEdit || (uri4 = this.croppedUri) == null) {
            str = this.imageUri.getFilePath();
        } else {
            str = FileHelper.stripFileProtocol(uri4.toString());
        }
        if (this.encodingType == 0) {
            try {
                exifHelper.createInFile(str);
                exifHelper.readExifData();
                i2 = exifHelper.getOrientation();
            } catch (IOException e) {
                e.printStackTrace();
            }
            Bitmap bitmap = null;
            if (!this.saveToPhotoAlbum) {
                uri = Uri.fromFile(new File(getPicturesPath()));
                if (!this.allowEdit || (uri3 = this.croppedUri) == null) {
                    writeUncompressedImage(this.imageUri.getFileUri(), uri);
                } else {
                    writeUncompressedImage(uri3, uri);
                }
                refreshGallery(uri);
            } else {
                uri = null;
            }
            if (i != 0) {
                Bitmap scaledAndRotatedBitmap = getScaledAndRotatedBitmap(str);
                bitmap = scaledAndRotatedBitmap == null ? (Bitmap) intent.getExtras().get(Transcoder.DATA_KEY) : scaledAndRotatedBitmap;
                if (bitmap == null) {
                    LOG.d(LOG_TAG, "I either have a null image path or bitmap");
                    failPicture("Unable to create bitmap!");
                    return;
                }
                processPicture(bitmap, this.encodingType);
                if (!this.saveToPhotoAlbum) {
                    checkForDuplicateImage(0);
                }
            } else if (i != 1 && i != 2) {
                throw new IllegalStateException();
            } else if (this.targetHeight != -1 || this.targetWidth != -1 || this.mQuality != 100 || this.correctOrientation) {
                Uri fromFile = Uri.fromFile(createCaptureFile(this.encodingType, System.currentTimeMillis() + ""));
                bitmap = getScaledAndRotatedBitmap(str);
                if (bitmap == null) {
                    LOG.d(LOG_TAG, "I either have a null image path or bitmap");
                    failPicture("Unable to create bitmap!");
                    return;
                }
                OutputStream openOutputStream = this.f30cordova.getActivity().getContentResolver().openOutputStream(fromFile);
                bitmap.compress(this.encodingType == 0 ? Bitmap.CompressFormat.JPEG : Bitmap.CompressFormat.PNG, this.mQuality, openOutputStream);
                openOutputStream.close();
                if (this.encodingType == 0) {
                    String path = fromFile.getPath();
                    if (i2 != 1) {
                        exifHelper.resetOrientation();
                    }
                    exifHelper.createOutFile(path);
                    exifHelper.writeExifData();
                }
                this.callbackContext.success(fromFile.toString());
            } else if (this.saveToPhotoAlbum) {
                this.callbackContext.success(uri.toString());
            } else {
                Uri fromFile2 = Uri.fromFile(createCaptureFile(this.encodingType, System.currentTimeMillis() + ""));
                if (!this.allowEdit || (uri2 = this.croppedUri) == null) {
                    writeUncompressedImage(this.imageUri.getFileUri(), fromFile2);
                } else {
                    writeUncompressedImage(Uri.fromFile(new File(getFileNameFromUri(uri2))), fromFile2);
                }
                this.callbackContext.success(fromFile2.toString());
            }
            cleanup(1, this.imageUri.getFileUri(), uri, bitmap);
        }
        i2 = 0;
        Bitmap bitmap2 = null;
        if (!this.saveToPhotoAlbum) {
        }
        if (i != 0) {
        }
        cleanup(1, this.imageUri.getFileUri(), uri, bitmap2);
    }

    private String getPicturesPath() {
        String format = new SimpleDateFormat(TIME_FORMAT).format(new Date());
        StringBuilder sb = new StringBuilder();
        sb.append("IMG_");
        sb.append(format);
        sb.append(this.encodingType == 0 ? JPEG_EXTENSION : PNG_EXTENSION);
        String sb2 = sb.toString();
        File externalStoragePublicDirectory = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES);
        externalStoragePublicDirectory.mkdirs();
        return externalStoragePublicDirectory.getAbsolutePath() + "/" + sb2;
    }

    private void refreshGallery(Uri uri) {
        Intent intent = new Intent("android.intent.action.MEDIA_SCANNER_SCAN_FILE");
        intent.setData(uri);
        this.f30cordova.getActivity().sendBroadcast(intent);
    }

    private String outputModifiedBitmap(Bitmap bitmap, Uri uri) throws IOException {
        String str;
        String realPath = FileHelper.getRealPath(uri, this.f30cordova);
        if (realPath != null) {
            str = realPath.substring(realPath.lastIndexOf(47) + 1);
        } else {
            StringBuilder sb = new StringBuilder();
            sb.append("modified.");
            sb.append(this.encodingType == 0 ? JPEG_TYPE : PNG_TYPE);
            str = sb.toString();
        }
        new SimpleDateFormat(TIME_FORMAT).format(new Date());
        String str2 = getTempDirectoryPath() + "/" + str;
        FileOutputStream fileOutputStream = new FileOutputStream(str2);
        bitmap.compress(this.encodingType == 0 ? Bitmap.CompressFormat.JPEG : Bitmap.CompressFormat.PNG, this.mQuality, fileOutputStream);
        fileOutputStream.close();
        ExifHelper exifHelper = this.exifData;
        if (exifHelper != null && this.encodingType == 0) {
            try {
                if (this.correctOrientation && this.orientationCorrected) {
                    exifHelper.resetOrientation();
                }
                this.exifData.createOutFile(str2);
                this.exifData.writeExifData();
                this.exifData = null;
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return str2;
    }

    /* access modifiers changed from: private */
    /* access modifiers changed from: public */
    private void processResultFromGallery(int i, Intent intent) {
        Uri data = intent.getData();
        if (data == null && (data = this.croppedUri) == null) {
            failPicture("null data from photo library");
            return;
        }
        String realPath = FileHelper.getRealPath(data, this.f30cordova);
        LOG.d(LOG_TAG, "File location is: " + realPath);
        String uri = data.toString();
        String mimeType = FileHelper.getMimeType(uri, this.f30cordova);
        if (this.mediaType == 1 || (!JPEG_MIME_TYPE.equalsIgnoreCase(mimeType) && !PNG_MIME_TYPE.equalsIgnoreCase(mimeType))) {
            this.callbackContext.success(realPath);
        } else if (this.targetHeight == -1 && this.targetWidth == -1 && ((i == 1 || i == 2) && !this.correctOrientation && mimeType != null && mimeType.equalsIgnoreCase(getMimetypeForFormat(this.encodingType)))) {
            this.callbackContext.success(uri);
        } else {
            Bitmap bitmap = null;
            try {
                bitmap = getScaledAndRotatedBitmap(uri);
            } catch (IOException e) {
                e.printStackTrace();
            }
            if (bitmap == null) {
                LOG.d(LOG_TAG, "I either have a null image path or bitmap");
                failPicture("Unable to create bitmap!");
                return;
            }
            if (i == 0) {
                processPicture(bitmap, this.encodingType);
            } else if (i == 1 || i == 2) {
                if ((this.targetHeight <= 0 || this.targetWidth <= 0) && ((!this.correctOrientation || !this.orientationCorrected) && mimeType.equalsIgnoreCase(getMimetypeForFormat(this.encodingType)))) {
                    this.callbackContext.success(realPath);
                } else {
                    try {
                        String outputModifiedBitmap = outputModifiedBitmap(bitmap, data);
                        CallbackContext callbackContext2 = this.callbackContext;
                        callbackContext2.success("file://" + outputModifiedBitmap + "?" + System.currentTimeMillis());
                    } catch (Exception e2) {
                        e2.printStackTrace();
                        failPicture("Error retrieving image.");
                    }
                }
            }
            if (bitmap != null) {
                bitmap.recycle();
            }
            System.gc();
        }
    }

    @Override // org.apache.cordova.CordovaPlugin
    public void onActivityResult(int i, int i2, final Intent intent) {
        int i3 = (i / 16) - 1;
        final int i4 = (i % 16) - 1;
        if (i >= 100) {
            if (i2 == -1) {
                try {
                    processResultFromCamera(i - 100, intent);
                } catch (IOException e) {
                    e.printStackTrace();
                    LOG.e(LOG_TAG, "Unable to write to file");
                }
            } else if (i2 == 0) {
                failPicture("No Image Selected");
            } else {
                failPicture("Did not complete!");
            }
        } else if (i3 == 1) {
            if (i2 == -1) {
                try {
                    if (this.allowEdit) {
                        Activity activity = this.f30cordova.getActivity();
                        performCrop(FileProvider.getUriForFile(activity, this.applicationId + ".provider", createCaptureFile(this.encodingType)), i4, intent);
                        return;
                    }
                    processResultFromCamera(i4, intent);
                } catch (IOException e2) {
                    e2.printStackTrace();
                    failPicture("Error capturing image.");
                }
            } else if (i2 == 0) {
                failPicture("No Image Selected");
            } else {
                failPicture("Did not complete!");
            }
        } else if (i3 != 0 && i3 != 2) {
        } else {
            if (i2 == -1 && intent != null) {
                this.f30cordova.getThreadPool().execute(new Runnable() {
                    /* class org.apache.cordova.camera.CameraLauncher.AnonymousClass1 */

                    public void run() {
                        CameraLauncher.this.processResultFromGallery(i4, intent);
                    }
                });
            } else if (i2 == 0) {
                failPicture("No Image Selected");
            } else {
                failPicture("Selection did not complete!");
            }
        }
    }

    private void writeUncompressedImage(InputStream inputStream, Uri uri) throws FileNotFoundException, IOException {
        OutputStream outputStream = null;
        try {
            outputStream = this.f30cordova.getActivity().getContentResolver().openOutputStream(uri);
            byte[] bArr = new byte[TasDefs.ADDITIONAL_DATA_MAX_LENGTH];
            while (true) {
                int read = inputStream.read(bArr);
                if (read == -1) {
                    break;
                }
                outputStream.write(bArr, 0, read);
            }
            outputStream.flush();
        } finally {
            if (outputStream != null) {
                try {
                    outputStream.close();
                } catch (IOException unused) {
                    LOG.d(LOG_TAG, "Exception while closing output stream.");
                }
            }
            if (inputStream != null) {
                try {
                    inputStream.close();
                } catch (IOException unused2) {
                    LOG.d(LOG_TAG, "Exception while closing file input stream.");
                }
            }
        }
    }

    private void writeUncompressedImage(Uri uri, Uri uri2) throws FileNotFoundException, IOException {
        writeUncompressedImage(new FileInputStream(FileHelper.stripFileProtocol(uri.toString())), uri2);
    }

    private Uri getUriFromMediaStore() {
        ContentValues contentValues = new ContentValues();
        contentValues.put("mime_type", JPEG_MIME_TYPE);
        try {
            return this.f30cordova.getActivity().getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues);
        } catch (RuntimeException unused) {
            LOG.d(LOG_TAG, "Can't write to external media storage.");
            try {
                return this.f30cordova.getActivity().getContentResolver().insert(MediaStore.Images.Media.INTERNAL_CONTENT_URI, contentValues);
            } catch (RuntimeException unused2) {
                LOG.d(LOG_TAG, "Can't write to internal media storage.");
                return null;
            }
        }
    }

    /* JADX DEBUG: Failed to insert an additional move for type inference into block B:6:0x000d */
    /* JADX WARN: Multi-variable type inference failed */
    /* JADX WARN: Type inference failed for: r14v25, types: [java.io.InputStream] */
    /* JADX WARN: Type inference failed for: r14v32 */
    /* JADX WARN: Type inference failed for: r14v37 */
    /* JADX WARN: Type inference failed for: r14v41 */
    /* JADX WARNING: Code restructure failed: missing block: B:10:0x0017, code lost:
        if (r14 != null) goto L_0x0019;
     */
    /* JADX WARNING: Code restructure failed: missing block: B:28:0x0045, code lost:
        if (r14 != null) goto L_0x0019;
     */
    /* JADX WARNING: Removed duplicated region for block: B:127:0x01f1 A[SYNTHETIC, Splitter:B:127:0x01f1] */
    /* JADX WARNING: Removed duplicated region for block: B:24:0x0039 A[Catch:{ OutOfMemoryError -> 0x003a, Exception -> 0x002c, all -> 0x0029, all -> 0x0049 }] */
    /* JADX WARNING: Removed duplicated region for block: B:32:0x004c A[SYNTHETIC, Splitter:B:32:0x004c] */
    /* JADX WARNING: Unknown variable types count: 1 */
    private Bitmap getScaledAndRotatedBitmap(String str) throws IOException {
        int i;
        File file;
        Uri uri;
        Throwable th;
        boolean z;
        int i2;
        int i3;
        InputStream inputStreamFromUriString;
        ?? r14;
        Throwable th2;
        InputStream inputStream;
        OutOfMemoryError e;
        Exception e2;
        InputStream inputStream2;
        InputStream inputStream3 = null;
        r1 = null;
        r1 = null;
        r1 = null;
        Bitmap bitmap = null;
        if (this.targetWidth > 0 || this.targetHeight > 0 || this.correctOrientation) {
            try {
                InputStream inputStreamFromUriString2 = FileHelper.getInputStreamFromUriString(str, this.f30cordova);
                if (inputStreamFromUriString2 != null) {
                    String format = new SimpleDateFormat(TIME_FORMAT).format(new Date());
                    StringBuilder sb = new StringBuilder();
                    sb.append("IMG_");
                    sb.append(format);
                    sb.append(this.encodingType == 0 ? JPEG_EXTENSION : PNG_EXTENSION);
                    file = new File(getTempDirectoryPath() + sb.toString());
                    uri = Uri.fromFile(file);
                    writeUncompressedImage(inputStreamFromUriString2, uri);
                    try {
                        if (JPEG_MIME_TYPE.equalsIgnoreCase(FileHelper.getMimeType(str.toString(), this.f30cordova))) {
                            String replace = uri.toString().replace("file://", "");
                            this.exifData = new ExifHelper();
                            this.exifData.createInFile(replace);
                            this.exifData.readExifData();
                            if (this.correctOrientation) {
                                i = exifToDegrees(new ExifInterface(replace).getAttributeInt("Orientation", 0));
                            }
                        }
                        i = 0;
                    } catch (Exception e3) {
                        LOG.w(LOG_TAG, "Unable to read Exif data: " + e3.toString());
                        i = 0;
                    }
                } else {
                    uri = null;
                    file = null;
                    i = 0;
                }
                try {
                    BitmapFactory.Options options = new BitmapFactory.Options();
                    options.inJustDecodeBounds = true;
                    try {
                        InputStream inputStreamFromUriString3 = FileHelper.getInputStreamFromUriString(uri.toString(), this.f30cordova);
                        try {
                            BitmapFactory.decodeStream(inputStreamFromUriString3, null, options);
                            if (inputStreamFromUriString3 != null) {
                                try {
                                    inputStreamFromUriString3.close();
                                } catch (IOException unused) {
                                    LOG.d(LOG_TAG, "Exception while closing file input stream.");
                                }
                            }
                            if (options.outWidth == 0 || options.outHeight == 0) {
                                if (file != null) {
                                    file.delete();
                                }
                                return null;
                            }
                            if (this.targetWidth <= 0 && this.targetHeight <= 0) {
                                this.targetWidth = options.outWidth;
                                this.targetHeight = options.outHeight;
                            }
                            if (i == 90 || i == 270) {
                                i3 = options.outHeight;
                                i2 = options.outWidth;
                                z = true;
                            } else {
                                i3 = options.outWidth;
                                i2 = options.outHeight;
                                z = false;
                            }
                            int[] calculateAspectRatio = calculateAspectRatio(i3, i2);
                            options.inJustDecodeBounds = false;
                            options.inSampleSize = calculateSampleSize(i3, i2, calculateAspectRatio[0], calculateAspectRatio[1]);
                            try {
                                inputStreamFromUriString = FileHelper.getInputStreamFromUriString(uri.toString(), this.f30cordova);
                                Bitmap decodeStream = BitmapFactory.decodeStream(inputStreamFromUriString, null, options);
                                if (decodeStream == null) {
                                    return null;
                                }
                                Bitmap createScaledBitmap = Bitmap.createScaledBitmap(decodeStream, !z ? calculateAspectRatio[0] : calculateAspectRatio[1], !z ? calculateAspectRatio[1] : calculateAspectRatio[0], true);
                                if (createScaledBitmap != decodeStream) {
                                    decodeStream.recycle();
                                }
                                if (this.correctOrientation && i != 0) {
                                    Matrix matrix = new Matrix();
                                    matrix.setRotate((float) i);
                                    try {
                                        createScaledBitmap = Bitmap.createBitmap(createScaledBitmap, 0, 0, createScaledBitmap.getWidth(), createScaledBitmap.getHeight(), matrix, true);
                                        this.orientationCorrected = true;
                                    } catch (OutOfMemoryError unused2) {
                                        this.orientationCorrected = false;
                                    }
                                }
                                if (file != null) {
                                    file.delete();
                                }
                                return createScaledBitmap;
                            } finally {
                                if (inputStreamFromUriString != null) {
                                    try {
                                        inputStreamFromUriString.close();
                                    } catch (IOException unused3) {
                                        LOG.d(LOG_TAG, "Exception while closing file input stream.");
                                    }
                                }
                            }
                        } catch (Throwable th3) {
                            th = th3;
                            inputStream3 = inputStreamFromUriString3;
                            if (inputStream3 != null) {
                                try {
                                    inputStream3.close();
                                } catch (IOException unused4) {
                                    LOG.d(LOG_TAG, "Exception while closing file input stream.");
                                }
                            }
                            throw th;
                        }
                    } catch (Throwable th4) {
                        th = th4;
                        if (inputStream3 != null) {
                        }
                        throw th;
                    }
                } finally {
                    if (file != null) {
                        file.delete();
                    }
                }
            } catch (Exception e4) {
                LOG.e(LOG_TAG, "Exception while getting input stream: " + e4.toString());
                return null;
            }
        } else {
            try {
                InputStream inputStreamFromUriString4 = FileHelper.getInputStreamFromUriString(str, this.f30cordova);
                try {
                    bitmap = BitmapFactory.decodeStream(inputStreamFromUriString4);
                    inputStream2 = inputStreamFromUriString4;
                } catch (OutOfMemoryError e5) {
                    e = e5;
                    inputStream = inputStreamFromUriString4;
                    this.callbackContext.error(e.getLocalizedMessage());
                    inputStream2 = inputStream;
                } catch (Exception e6) {
                    e2 = e6;
                    str = inputStreamFromUriString4;
                    this.callbackContext.error(e2.getLocalizedMessage());
                    if (str != null) {
                    }
                    return bitmap;
                }
            } catch (OutOfMemoryError e7) {
                e = e7;
                inputStream = null;
                this.callbackContext.error(e.getLocalizedMessage());
                inputStream2 = inputStream;
            } catch (Exception e8) {
                e2 = e8;
                str = null;
                this.callbackContext.error(e2.getLocalizedMessage());
                if (str != null) {
                    inputStream2 = str;
                    try {
                        inputStream2.close();
                    } catch (IOException unused5) {
                        LOG.d(LOG_TAG, "Exception while closing file input stream.");
                    }
                }
                return bitmap;
            } catch (Throwable th5) {
                th2 = th5;
                r14 = str;
                if (r14 != 0) {
                }
                throw th2;
            }
        }
    }

    public int[] calculateAspectRatio(int i, int i2) {
        int i3 = this.targetWidth;
        int i4 = this.targetHeight;
        if (i3 > 0 || i4 > 0) {
            if (i3 > 0 && i4 <= 0) {
                double d = (double) i3;
                double d2 = (double) i;
                Double.isNaN(d);
                Double.isNaN(d2);
                double d3 = (double) i2;
                Double.isNaN(d3);
                i2 = (int) ((d / d2) * d3);
                i = i3;
            } else if (i3 > 0 || i4 <= 0) {
                double d4 = (double) i3;
                double d5 = (double) i4;
                Double.isNaN(d4);
                Double.isNaN(d5);
                double d6 = d4 / d5;
                double d7 = (double) i;
                double d8 = (double) i2;
                Double.isNaN(d7);
                Double.isNaN(d8);
                double d9 = d7 / d8;
                if (d9 > d6) {
                    i2 = (i2 * i3) / i;
                    i = i3;
                } else if (d9 < d6) {
                    i = (i * i4) / i2;
                    i2 = i4;
                } else {
                    i = i3;
                    i2 = i4;
                }
            } else {
                double d10 = (double) i4;
                double d11 = (double) i2;
                Double.isNaN(d10);
                Double.isNaN(d11);
                double d12 = (double) i;
                Double.isNaN(d12);
                i = (int) ((d10 / d11) * d12);
                i2 = i4;
            }
        }
        return new int[]{i, i2};
    }

    public static int calculateSampleSize(int i, int i2, int i3, int i4) {
        if (((float) i) / ((float) i2) > ((float) i3) / ((float) i4)) {
            return i / i3;
        }
        return i2 / i4;
    }

    private Cursor queryImgDB(Uri uri) {
        return this.f30cordova.getActivity().getContentResolver().query(uri, new String[]{"_id"}, null, null, null);
    }

    private void cleanup(int i, Uri uri, Uri uri2, Bitmap bitmap) {
        if (bitmap != null) {
            bitmap.recycle();
        }
        new File(FileHelper.stripFileProtocol(uri.toString())).delete();
        checkForDuplicateImage(i);
        if (this.saveToPhotoAlbum && uri2 != null) {
            scanForGallery(uri2);
        }
        System.gc();
    }

    private void checkForDuplicateImage(int i) {
        Uri whichContentStore = whichContentStore();
        Cursor queryImgDB = queryImgDB(whichContentStore);
        int count = queryImgDB.getCount();
        int i2 = 1;
        if (i == 1 && this.saveToPhotoAlbum) {
            i2 = 2;
        }
        if (count - this.numPics == i2) {
            queryImgDB.moveToLast();
            int intValue = Integer.valueOf(queryImgDB.getString(queryImgDB.getColumnIndex("_id"))).intValue();
            if (i2 == 2) {
                intValue--;
            }
            this.f30cordova.getActivity().getContentResolver().delete(Uri.parse(whichContentStore + "/" + intValue), null, null);
            queryImgDB.close();
        }
    }

    private Uri whichContentStore() {
        if (Environment.getExternalStorageState().equals("mounted")) {
            return MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
        }
        return MediaStore.Images.Media.INTERNAL_CONTENT_URI;
    }

    public void processPicture(Bitmap bitmap, int i) {
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        try {
            if (bitmap.compress(i == 0 ? Bitmap.CompressFormat.JPEG : Bitmap.CompressFormat.PNG, this.mQuality, byteArrayOutputStream)) {
                this.callbackContext.success(new String(Base64.encode(byteArrayOutputStream.toByteArray(), 2)));
            }
        } catch (Exception unused) {
            failPicture("Error compressing image.");
        }
    }

    public void failPicture(String str) {
        this.callbackContext.error(str);
    }

    private void scanForGallery(Uri uri) {
        this.scanMe = uri;
        MediaScannerConnection mediaScannerConnection = this.conn;
        if (mediaScannerConnection != null) {
            mediaScannerConnection.disconnect();
        }
        this.conn = new MediaScannerConnection(this.f30cordova.getActivity().getApplicationContext(), this);
        this.conn.connect();
    }

    public void onMediaScannerConnected() {
        try {
            this.conn.scanFile(this.scanMe.toString(), "image/*");
        } catch (IllegalStateException unused) {
            LOG.e(LOG_TAG, "Can't scan file in MediaScanner after taking picture");
        }
    }

    public void onScanCompleted(String str, Uri uri) {
        this.conn.disconnect();
    }

    @Override // org.apache.cordova.CordovaPlugin
    public void onRequestPermissionResult(int i, String[] strArr, int[] iArr) throws JSONException {
        for (int i2 : iArr) {
            if (i2 == -1) {
                this.callbackContext.sendPluginResult(new PluginResult(PluginResult.Status.ERROR, 20));
                return;
            }
        }
        switch (i) {
            case 0:
                takePicture(this.destType, this.encodingType);
                return;
            case 1:
                getImage(this.srcType, this.destType, this.encodingType);
                return;
            default:
                return;
        }
    }

    @Override // org.apache.cordova.CordovaPlugin
    public Bundle onSaveInstanceState() {
        Bundle bundle = new Bundle();
        bundle.putInt("destType", this.destType);
        bundle.putInt("srcType", this.srcType);
        bundle.putInt("mQuality", this.mQuality);
        bundle.putInt("targetWidth", this.targetWidth);
        bundle.putInt("targetHeight", this.targetHeight);
        bundle.putInt("encodingType", this.encodingType);
        bundle.putInt("mediaType", this.mediaType);
        bundle.putInt("numPics", this.numPics);
        bundle.putBoolean("allowEdit", this.allowEdit);
        bundle.putBoolean("correctOrientation", this.correctOrientation);
        bundle.putBoolean("saveToPhotoAlbum", this.saveToPhotoAlbum);
        Uri uri = this.croppedUri;
        if (uri != null) {
            bundle.putString(CROPPED_URI_KEY, uri.toString());
        }
        CordovaUri cordovaUri = this.imageUri;
        if (cordovaUri != null) {
            bundle.putString(IMAGE_URI_KEY, cordovaUri.getFileUri().toString());
        }
        return bundle;
    }

    @Override // org.apache.cordova.CordovaPlugin
    public void onRestoreStateForActivityResult(Bundle bundle, CallbackContext callbackContext2) {
        this.destType = bundle.getInt("destType");
        this.srcType = bundle.getInt("srcType");
        this.mQuality = bundle.getInt("mQuality");
        this.targetWidth = bundle.getInt("targetWidth");
        this.targetHeight = bundle.getInt("targetHeight");
        this.encodingType = bundle.getInt("encodingType");
        this.mediaType = bundle.getInt("mediaType");
        this.numPics = bundle.getInt("numPics");
        this.allowEdit = bundle.getBoolean("allowEdit");
        this.correctOrientation = bundle.getBoolean("correctOrientation");
        this.saveToPhotoAlbum = bundle.getBoolean("saveToPhotoAlbum");
        if (bundle.containsKey(CROPPED_URI_KEY)) {
            this.croppedUri = Uri.parse(bundle.getString(CROPPED_URI_KEY));
        }
        if (bundle.containsKey(IMAGE_URI_KEY)) {
            this.imageUri = new CordovaUri(Uri.parse(bundle.getString(IMAGE_URI_KEY)));
        }
        this.callbackContext = callbackContext2;
    }

    private String getFileNameFromUri(Uri uri) {
        String str = uri.toString().split("external_files")[1];
        File externalStorageDirectory = Environment.getExternalStorageDirectory();
        return externalStorageDirectory.getAbsolutePath() + str;
    }
}
