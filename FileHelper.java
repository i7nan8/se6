package org.apache.cordova.camera;

import android.annotation.SuppressLint;
import android.content.ContentUris;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import android.webkit.MimeTypeMap;
import com.google.firebase.analytics.FirebaseAnalytics;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;
import org.apache.cordova.CordovaInterface;

public class FileHelper {
    private static final String LOG_TAG = "FileUtils";
    private static final String _DATA = "_data";

    public static String getRealPath(Uri uri, CordovaInterface cordovaInterface) {
        if (Build.VERSION.SDK_INT < 11) {
            return getRealPathFromURI_BelowAPI11(cordovaInterface.getActivity(), uri);
        }
        return getRealPathFromURI_API11_And_Above(cordovaInterface.getActivity(), uri);
    }

    public static String getRealPath(String str, CordovaInterface cordovaInterface) {
        return getRealPath(Uri.parse(str), cordovaInterface);
    }

    @SuppressLint({"NewApi"})
    public static String getRealPathFromURI_API11_And_Above(Context context, Uri uri) {
        Uri uri2 = null;
        if (!(Build.VERSION.SDK_INT >= 19) || !DocumentsContract.isDocumentUri(context, uri)) {
            if (FirebaseAnalytics.Param.CONTENT.equalsIgnoreCase(uri.getScheme())) {
                if (isGooglePhotosUri(uri)) {
                    return uri.getLastPathSegment();
                }
                return getDataColumn(context, uri, null, null);
            } else if ("file".equalsIgnoreCase(uri.getScheme())) {
                return uri.getPath();
            }
        } else if (isExternalStorageDocument(uri)) {
            String[] split = DocumentsContract.getDocumentId(uri).split(":");
            if ("primary".equalsIgnoreCase(split[0])) {
                return Environment.getExternalStorageDirectory() + "/" + split[1];
            }
        } else if (isDownloadsDocument(uri)) {
            String documentId = DocumentsContract.getDocumentId(uri);
            if (documentId == null || documentId.length() <= 0) {
                return null;
            }
            if (documentId.startsWith("raw:")) {
                return documentId.replaceFirst("raw:", "");
            }
            try {
                return getDataColumn(context, ContentUris.withAppendedId(Uri.parse("content://downloads/public_downloads"), Long.valueOf(documentId).longValue()), null, null);
            } catch (NumberFormatException unused) {
                return null;
            }
        } else if (isMediaDocument(uri)) {
            String[] split2 = DocumentsContract.getDocumentId(uri).split(":");
            String str = split2[0];
            if ("image".equals(str)) {
                uri2 = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
            } else if ("video".equals(str)) {
                uri2 = MediaStore.Video.Media.EXTERNAL_CONTENT_URI;
            } else if ("audio".equals(str)) {
                uri2 = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
            }
            return getDataColumn(context, uri2, "_id=?", new String[]{split2[1]});
        }
        return null;
    }

    public static String getRealPathFromURI_BelowAPI11(Context context, Uri uri) {
        try {
            Cursor query = context.getContentResolver().query(uri, new String[]{_DATA}, null, null, null);
            int columnIndexOrThrow = query.getColumnIndexOrThrow(_DATA);
            query.moveToFirst();
            return query.getString(columnIndexOrThrow);
        } catch (Exception unused) {
            return null;
        }
    }

    public static InputStream getInputStreamFromUriString(String str, CordovaInterface cordovaInterface) throws IOException {
        InputStream inputStream;
        if (str.startsWith(FirebaseAnalytics.Param.CONTENT)) {
            return cordovaInterface.getActivity().getContentResolver().openInputStream(Uri.parse(str));
        } else if (!str.startsWith("file://")) {
            return new FileInputStream(str);
        } else {
            int indexOf = str.indexOf("?");
            if (indexOf > -1) {
                str = str.substring(0, indexOf);
            }
            if (str.startsWith("file:///android_asset/")) {
                return cordovaInterface.getActivity().getAssets().open(Uri.parse(str).getPath().substring(15));
            }
            try {
                inputStream = cordovaInterface.getActivity().getContentResolver().openInputStream(Uri.parse(str));
            } catch (Exception unused) {
                inputStream = null;
            }
            if (inputStream == null) {
                return new FileInputStream(getRealPath(str, cordovaInterface));
            }
            return inputStream;
        }
    }

    public static String stripFileProtocol(String str) {
        return str.startsWith("file://") ? str.substring(7) : str;
    }

    public static String getMimeTypeForExtension(String str) {
        int lastIndexOf = str.lastIndexOf(46);
        if (lastIndexOf != -1) {
            str = str.substring(lastIndexOf + 1);
        }
        String lowerCase = str.toLowerCase(Locale.getDefault());
        if (lowerCase.equals("3ga")) {
            return "audio/3gpp";
        }
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(lowerCase);
    }

    public static String getMimeType(String str, CordovaInterface cordovaInterface) {
        Uri parse = Uri.parse(str);
        if (str.startsWith("content://")) {
            return cordovaInterface.getActivity().getContentResolver().getType(parse);
        }
        return getMimeTypeForExtension(parse.getPath());
    }

    /* JADX WARNING: Removed duplicated region for block: B:19:0x0039  */
    /* JADX WARNING: Removed duplicated region for block: B:24:0x0040  */
    public static String getDataColumn(Context context, Uri uri, String str, String[] strArr) {
        Cursor cursor;
        Throwable th;
        try {
            cursor = context.getContentResolver().query(uri, new String[]{_DATA}, str, strArr, null);
            if (cursor != null) {
                try {
                    if (cursor.moveToFirst()) {
                        String string = cursor.getString(cursor.getColumnIndexOrThrow(_DATA));
                        if (cursor != null) {
                            cursor.close();
                        }
                        return string;
                    }
                } catch (Exception unused) {
                    if (cursor != null) {
                    }
                    return null;
                } catch (Throwable th2) {
                    th = th2;
                    if (cursor != null) {
                    }
                    throw th;
                }
            }
            if (cursor != null) {
                cursor.close();
            }
            return null;
        } catch (Exception unused2) {
            cursor = null;
            if (cursor != null) {
                cursor.close();
            }
            return null;
        } catch (Throwable th3) {
            th = th3;
            cursor = null;
            if (cursor != null) {
                cursor.close();
            }
            throw th;
        }
    }

    public static boolean isExternalStorageDocument(Uri uri) {
        return "com.android.externalstorage.documents".equals(uri.getAuthority());
    }

    public static boolean isDownloadsDocument(Uri uri) {
        return "com.android.providers.downloads.documents".equals(uri.getAuthority());
    }

    public static boolean isMediaDocument(Uri uri) {
        return "com.android.providers.media.documents".equals(uri.getAuthority());
    }

    public static boolean isGooglePhotosUri(Uri uri) {
        return "com.google.android.apps.photos.content".equals(uri.getAuthority());
    }
}
