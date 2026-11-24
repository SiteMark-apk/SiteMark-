package com.sitemark.camera;

import android.Manifest;
import android.app.Activity;
import android.content.ClipData;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.print.PrintManager;
import android.provider.MediaStore;
import android.util.Base64;
import android.webkit.GeolocationPermissions;
import android.webkit.PermissionRequest;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;
import androidx.core.content.FileProvider;
import androidx.webkit.WebViewAssetLoader;
import androidx.webkit.WebViewCompat;
import androidx.webkit.WebViewFeature;
import org.json.JSONObject;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Date;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class SiteMarkActivity extends Activity {
    private static final String ORIGIN = "https://appassets.androidplatform.net";
    private static final String HOME = ORIGIN + "/assets/index.html";
    private static final int PERMISSIONS = 100, FILES = 101;
    private WebView web;
    private boolean loaded;
    private ValueCallback<Uri[]> fileCallback;
    private Uri captureUri;
    private volatile Uri lastGalleryUri;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();

    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        getWindow().setStatusBarColor(Color.rgb(21,25,30));
        getWindow().setNavigationBarColor(Color.rgb(21,25,30));
        web = new WebView(this);
        web.setBackgroundColor(Color.rgb(21,25,30));
        setContentView(web);
        final WebSettings settings = web.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setGeolocationEnabled(true);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(true);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setSupportMultipleWindows(false);
        final WebViewAssetLoader assets = new WebViewAssetLoader.Builder()
            .addPathHandler("/assets/", new WebViewAssetLoader.AssetsPathHandler(this)).build();
        web.setWebViewClient(new WebViewClient() {
            @Override public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                return assets.shouldInterceptRequest(request.getUrl());
            }
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri uri = request.getUrl();
                if (isLocal(uri)) return false;
                if (request.isForMainFrame() && "https".equals(uri.getScheme())) {
                    try { startActivity(new Intent(Intent.ACTION_VIEW, uri)); } catch (Exception ignored) { }
                }
                return true;
            }
        });
        web.setWebChromeClient(new WebChromeClient() {
            @Override public void onPermissionRequest(PermissionRequest request) {
                runOnUiThread(() -> {
                    if (!isLocal(request.getOrigin()) || !has(Manifest.permission.CAMERA)) { request.deny(); return; }
                    for (String resource : request.getResources()) {
                        if (PermissionRequest.RESOURCE_VIDEO_CAPTURE.equals(resource)) {
                            request.grant(new String[]{PermissionRequest.RESOURCE_VIDEO_CAPTURE}); return;
                        }
                    }
                    request.deny();
                });
            }
            @Override public void onGeolocationPermissionsShowPrompt(String origin, GeolocationPermissions.Callback callback) {
                callback.invoke(origin, isLocal(Uri.parse(origin)) && (has(Manifest.permission.ACCESS_FINE_LOCATION) || has(Manifest.permission.ACCESS_COARSE_LOCATION)), false);
            }
            @Override public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback, FileChooserParams params) {
                if (fileCallback != null) fileCallback.onReceiveValue(null);
                fileCallback = callback; captureUri = null;
                if (params.isCaptureEnabled() && has(Manifest.permission.CAMERA)) {
                    try {
                        File directory = new File(getCacheDir(), "phone-camera");
                        if (!directory.exists() && !directory.mkdirs()) throw new IOException("Camera cache unavailable");
                        File file = File.createTempFile("capture-", ".jpg", directory);
                        captureUri = FileProvider.getUriForFile(SiteMarkActivity.this, getPackageName()+".fileprovider", file);
                        Intent camera = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
                        camera.putExtra(MediaStore.EXTRA_OUTPUT, captureUri);
                        camera.setClipData(ClipData.newRawUri("SiteMark", captureUri));
                        camera.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                        startActivityForResult(camera, FILES); return true;
                    } catch (Exception ignored) { captureUri = null; }
                }
                try {
                    Intent pick = new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("image/*");
                    pick.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, params.getMode() == FileChooserParams.MODE_OPEN_MULTIPLE);
                    startActivityForResult(pick, FILES);
                } catch (Exception failure) { fileCallback.onReceiveValue(null); fileCallback = null; }
                return true;
            }
        });
        if (WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) {
            WebViewCompat.addWebMessageListener(web, "SiteMarkNative", Collections.singleton(ORIGIN),
                (view, message, sourceOrigin, isMainFrame, reply) -> {
                    if (!isMainFrame || !isLocal(sourceOrigin)) return;
                    String payload = message.getData();
                    if (payload == null || payload.length() > 24_000_000) return;
                    try {
                        JSONObject command = new JSONObject(payload);
                        String type = command.optString("type"), id = command.optString("id");
                        if ("savePhoto".equals(type)) {
                            String image = command.optString("image");
                            worker.execute(() -> {
                                try { Uri uri = saveJpeg(image); lastGalleryUri = uri; runOnUiThread(() -> { try { if (!isFinishing()) reply.postMessage(result(id,true,"Saved to Pictures/SiteMark")); } catch (Exception ignored) {} }); }
                                catch (Exception error) { runOnUiThread(() -> { try { if (!isFinishing()) reply.postMessage(result(id,false,"Could not save to phone Gallery")); } catch (Exception ignored) {} }); }
                            });
                        } else if ("print".equals(type)) {
                            PrintManager pm = (PrintManager)getSystemService(PRINT_SERVICE);
                            if (pm != null) pm.print("SiteMark inspection report", web.createPrintDocumentAdapter("SiteMark inspection report"), null);
                        } else if ("openLatest".equals(type) && lastGalleryUri != null) {
                            try { startActivity(new Intent(Intent.ACTION_VIEW).setDataAndType(lastGalleryUri,"image/jpeg").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)); }
                            catch (Exception ignored) { Toast.makeText(this,"Open your phone Gallery, then SiteMark album",Toast.LENGTH_LONG).show(); }
                        }
                    } catch (Exception ignored) { }
                });
        } else Toast.makeText(this,"Update Android System WebView for Gallery saving",Toast.LENGTH_LONG).show();
        pruneCameraCache();
        if (!has(Manifest.permission.CAMERA) || (!has(Manifest.permission.ACCESS_FINE_LOCATION) && !has(Manifest.permission.ACCESS_COARSE_LOCATION))) {
            requestPermissions(new String[]{Manifest.permission.CAMERA, Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION}, PERMISSIONS);
        } else loadHome();
    }

    private boolean has(String permission) { return checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED; }
    private boolean isLocal(Uri uri) { return uri != null && "https".equals(uri.getScheme()) && "appassets.androidplatform.net".equals(uri.getHost()) && (uri.getPort() == -1 || uri.getPort() == 443); }
    private void loadHome() { if (!loaded) { loaded = true; web.loadUrl(HOME); } }
    private String result(String id, boolean ok, String message) { try { return new JSONObject().put("id",id).put("ok",ok).put("message",message).toString(); } catch (Exception ignored) { return "{}"; } }

    private Uri saveJpeg(String data) throws IOException {
        final String prefix = "data:image/jpeg;base64,";
        if (data == null || !data.startsWith(prefix) || data.length() > 24_000_000) throw new IOException("Invalid photo");
        byte[] bytes = Base64.decode(data.substring(prefix.length()), Base64.DEFAULT);
        if (bytes.length < 4 || bytes.length > 16_000_000 || (bytes[0]&255)!=255 || (bytes[1]&255)!=216) throw new IOException("Invalid JPEG");
        BitmapFactory.Options options = new BitmapFactory.Options(); options.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(bytes,0,bytes.length,options);
        if (options.outWidth < 1 || options.outHeight < 1 || options.outWidth > 10000 || options.outHeight > 10000 || !"image/jpeg".equals(options.outMimeType)) throw new IOException("Invalid image dimensions");
        String name = "SiteMark_" + new SimpleDateFormat("yyyyMMdd_HHmmss_SSS",Locale.ROOT).format(new Date()) + "_" + UUID.randomUUID().toString().substring(0,6) + ".jpg";
        ContentValues values = new ContentValues();
        values.put(MediaStore.Images.Media.DISPLAY_NAME,name);
        values.put(MediaStore.Images.Media.MIME_TYPE,"image/jpeg");
        values.put(MediaStore.Images.Media.RELATIVE_PATH,Environment.DIRECTORY_PICTURES + "/SiteMark");
        values.put(MediaStore.Images.Media.IS_PENDING,1);
        ContentResolver resolver = getContentResolver();
        Uri uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,values);
        if (uri == null) throw new IOException("Unable to create Gallery image");
        try {
            try (OutputStream out = resolver.openOutputStream(uri,"w")) { if (out == null) throw new IOException("No output stream"); out.write(bytes); }
            ContentValues complete = new ContentValues(); complete.put(MediaStore.Images.Media.IS_PENDING,0);
            if (resolver.update(uri,complete,null,null) < 1) throw new IOException("Unable to publish Gallery image");
            return uri;
        } catch (Exception e) { resolver.delete(uri,null,null); throw new IOException(e); }
    }

    private void pruneCameraCache() { File dir = new File(getCacheDir(),"phone-camera"); File[] files = dir.listFiles(); if (files != null) for (File f:files) if (f.isFile() && System.currentTimeMillis()-f.lastModified()>86400000L) f.delete(); }
    @Override public void onRequestPermissionsResult(int requestCode,String[] permissions,int[] results) { super.onRequestPermissionsResult(requestCode,permissions,results); if(requestCode==PERMISSIONS)loadHome(); }
    @Override protected void onActivityResult(int requestCode,int resultCode,Intent data) {
        super.onActivityResult(requestCode,resultCode,data);
        if(requestCode==FILES && fileCallback!=null) {
            Uri[] uris=null;
            if(resultCode==RESULT_OK) {
                if(captureUri!=null)uris=new Uri[]{captureUri};
                else if(data!=null && data.getClipData()!=null) { ClipData clip=data.getClipData(); uris=new Uri[clip.getItemCount()]; for(int i=0;i<uris.length;i++)uris[i]=clip.getItemAt(i).getUri(); }
                else if(data!=null && data.getData()!=null)uris=new Uri[]{data.getData()};
            }
            fileCallback.onReceiveValue(uris); fileCallback=null; captureUri=null;
        }
    }
    @Override protected void onPause() { if(web!=null){web.evaluateJavascript("if(typeof stopLive==='function')stopLive();if(typeof stopAutoGPS==='function')stopAutoGPS();",null);web.onPause();}super.onPause(); }
    @Override protected void onResume() { super.onResume();if(web!=null){web.onResume();if(loaded)web.evaluateJavascript("if(typeof ready!=='undefined'&&ready&&screenMode==='camera'){cameraView();}",null);} }
    @Override public void onBackPressed() { if(web==null){super.onBackPressed();return;}web.evaluateJavascript("(function(){if(typeof dialog!=='undefined'&&dialog.open){dialog.close();return true}if(typeof screenMode!=='undefined'&&screenMode!=='camera'){navigate('camera');return true}return false})()",v->{if(!"true".equals(v))finish();}); }
    @Override protected void onDestroy() { if(web!=null){web.destroy();web=null;}worker.shutdown();super.onDestroy(); }
}
