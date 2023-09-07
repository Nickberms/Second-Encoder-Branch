package om.sstvencoder;

import android.Manifest;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Bitmap;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.provider.MediaStore;
import android.system.ErrnoException;
import android.system.OsConstants;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.exifinterface.media.ExifInterface;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.facebook.stetho.Stetho;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import om.sstvencoder.ModeInterfaces.IModeInfo;
import om.sstvencoder.Output.WaveFileOutputContext;

public class MainActivity extends AppCompatActivity {
    private static final int REQUEST_LOAD_IMAGE_PERMISSION = 1;
    private static final int REQUEST_SAVE_WAVE_PERMISSION = 2;
    private static final int REQUEST_IMAGE_CAPTURE_PERMISSION = 3;
    private static final int REQUEST_PICK_IMAGE = 11;
    private static final int REQUEST_IMAGE_CAPTURE = 12;
    private static final int REQUEST_LOCATION_PERMISSION = 4;
    private Settings mSettings;
    private CropView cpvDisplayPicture;
    private Encoder mEncoder;
    private EditText edtEnterText;
    private Button btnDeletePicture;
    private MyDatabaseHelper dbHelper;
    private LocationHelper locationHelper;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        File databaseFile = getApplicationContext().getDatabasePath("encoder_database.db");
        Log.d("MainActivity", "Database file path: " + databaseFile.getAbsolutePath());
        Stetho.initializeWithDefaults(this);
        displayNewData();
        if (!checkLocationPermission()) {
            requestLocationPermission();
        }
        btnDeletePicture = findViewById(R.id.btnDeletePicture);
        cpvDisplayPicture = findViewById(R.id.cpvDisplayPicture);
        cpvDisplayPicture.setVisibility(View.GONE);
        boolean isRestarted = isAppRestarted();
        if (!isRestarted) {
            cpvDisplayPicture.setVisibility(View.GONE);
            destroyPreviousData();
        }
        Button btnTakePicture = findViewById(R.id.btnTakePicture);
        btnTakePicture.setOnClickListener(v -> takePicture());
        Button btnPickPicture = findViewById(R.id.btnPickPicture);
        btnPickPicture.setOnClickListener(v -> dispatchPickPictureIntent());
        Button btnGetLocation = findViewById(R.id.btnGetLocation);
        btnGetLocation.setOnClickListener(v -> getLocationAndUpdateTextField());
        edtEnterText = findViewById(R.id.edtEnterText);
        Button btnStopEncoding = findViewById(R.id.btnStopEncoding);
        btnStopEncoding.setOnClickListener(v -> stopEncoding());
        Button btnStartEncoding = findViewById(R.id.btnStartEncoding);
        btnStartEncoding.setOnClickListener(v -> startEncoding());
        MainActivityMessenger messenger = new MainActivityMessenger(this);
        mEncoder = new Encoder(messenger);
        mSettings = new Settings(this);
        mSettings.load();
        setMode(mSettings.getModeClassName());
    }

    public void copyText(View view) {
        TextView textView = (TextView) view;
        String textToCopy = textView.getText().toString();
        if (isCoordinateFormat(textToCopy)) {
            openMapsApp(textToCopy);
        } else {
            ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            ClipData clip = ClipData.newPlainText("Copied Text", textToCopy);
            clipboard.setPrimaryClip(clip);
            Toast.makeText(this, "Text copied to clipboard", Toast.LENGTH_SHORT).show();
        }
    }

    private boolean isCoordinateFormat(String text) {
        String[] parts = text.split(",");
        if (parts.length == 2) {
            try {
                double latitude = Double.parseDouble(parts[0]);
                double longitude = Double.parseDouble(parts[1]);
                if (latitude >= -90 && latitude <= 90 && longitude >= -180 && longitude <= 180) {
                    return true;
                }
            } catch (NumberFormatException ignored) {
            }
        }
        return false;
    }

    private void openMapsApp(String coordinate) {
        Uri geoUri = Uri.parse("geo:" + coordinate + "?q=" + coordinate);
        Intent mapIntent = new Intent(Intent.ACTION_VIEW, geoUri);
        mapIntent.setPackage("com.google.android.apps.maps");
        if (mapIntent.resolveActivity(getPackageManager()) != null) {
            startActivity(mapIntent);
        } else {
            Toast.makeText(this, "Please install Google Maps first", Toast.LENGTH_SHORT).show();
        }
    }

    private void getLocationAndUpdateTextField() {
        if (checkLocationPermission()) {
            LocationManager locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
            if (locationManager != null && !locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                showMessage("Please turn on the device location");
            } else {
                if (locationHelper == null) {
                    locationHelper = new LocationHelper(this, edtEnterText);
                }
                locationHelper.startLocationUpdates();
            }
        } else {
            requestLocationPermission();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        switch (requestCode) {
            case REQUEST_LOAD_IMAGE_PERMISSION:
                if (permissionGranted(grantResults))
                    loadImage(mSettings.getImageUri());
                else
                    setDefaultBitmap();
                break;
            case REQUEST_IMAGE_CAPTURE_PERMISSION:
                if (permissionGranted(grantResults))
                    dispatchTakePictureIntent();
                break;
            case REQUEST_SAVE_WAVE_PERMISSION:
                if (permissionGranted(grantResults))
                    saveWave();
                break;
            case REQUEST_LOCATION_PERMISSION:
                if (permissionGranted(grantResults)) {
                    showMessage("Location permission successfully granted");
                } else {
                    showMessage("Location permission not granted");
                    int delayMillis = 3000;
                    new Handler().postDelayed(() -> showMessage("You can grant the app location permission in Settings"), delayMillis);
                }
                break;
            default:
                break;
        }
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    private boolean checkLocationPermission() {
        return ContextCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    private void requestLocationPermission() {
        ActivityCompat.requestPermissions(this,
                new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                REQUEST_LOCATION_PERMISSION);
    }

    private boolean permissionGranted(@NonNull int[] grantResults) {
        return grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED;
    }

    private boolean isAppRestarted() {
        SharedPreferences preferences = getSharedPreferences("AppPreferences", MODE_PRIVATE);
        boolean isRestarted = preferences.getBoolean("IsRestarted", false);
        SharedPreferences.Editor editor = preferences.edit();
        editor.putBoolean("IsRestarted", true);
        editor.apply();
        return isRestarted;
    }

    private void destroyPreviousData() {
        File previousImageFile = new File(getFilesDir(), "uploaded_image.jpg");
        if (previousImageFile.exists()) {
            cpvDisplayPicture.setVisibility(View.GONE);
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        cpvDisplayPicture.setVisibility(View.GONE);
        loadImage(intent);
    }

    private void loadImage(Intent intent) {
        Uri uri = getImageUriFromIntent(intent);
        if (uri == null) {
            uri = mSettings.getImageUri();
        }
        boolean succeeded = loadImage(uri);
        if (!succeeded) {
            resetState();
        }
    }

    private Uri getImageUriFromIntent(Intent intent) {
        cpvDisplayPicture.setVisibility(View.GONE);
        Uri uri = null;
        if (isIntentTypeValid(intent.getType()) && isIntentActionValid(intent.getAction())) {
            uri = intent.hasExtra(Intent.EXTRA_STREAM) ?
                    (Uri) intent.getParcelableExtra(Intent.EXTRA_STREAM) : intent.getData();
        }
        return uri;
    }

    private boolean loadImage(Uri uri) {
        boolean succeeded = false;
        ContentResolver resolver = getContentResolver();
        if (uri != null) {
            cpvDisplayPicture.setVisibility(View.VISIBLE);
            try {
                InputStream stream = resolver.openInputStream(uri);
                if (stream != null) {
                    cpvDisplayPicture.setBitmap(stream);
                    succeeded = true;
                    btnDeletePicture.setOnClickListener(v -> resetState());
                }
            } catch (Exception ex) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && isPermissionException(ex)
                        && needsRequestReadPermission()) {
                    requestReadPermission();
                }
            }
        } else {
            cpvDisplayPicture.setVisibility(View.GONE);
        }
        if (succeeded) {
            cpvDisplayPicture.rotateImage(getOrientation(resolver, uri));
            mSettings.setImageUri(uri);
        }
        return succeeded;
    }

    private void resetState() {
        try {
            cpvDisplayPicture.setBitmap(null);
            mSettings.setImageUri(null);
        } catch (Exception e) {
            e.printStackTrace();
        }
        cpvDisplayPicture.setVisibility(View.GONE);
        mSettings.setImageUri(null);
    }

    private void setDefaultBitmap() {
        try {
            cpvDisplayPicture.setBitmap(getResources().openRawResource(R.raw.color_bars));
        } catch (Exception ignore) {
            cpvDisplayPicture.setNoBitmap();
        }
        mSettings.setImageUri(null);
    }

    private boolean isIntentActionValid(String action) {
        return Intent.ACTION_SEND.equals(action);
    }

    private boolean isIntentTypeValid(String type) {
        return type != null && type.startsWith("image/");
    }

    private boolean isPermissionException(Exception ex) {
        return ex.getCause() instanceof ErrnoException
                && ((ErrnoException) ex.getCause()).errno == OsConstants.EACCES;
    }

    private boolean needsRequestReadPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M)
            return false;
        String permission = Manifest.permission.READ_EXTERNAL_STORAGE;
        int state = ContextCompat.checkSelfPermission(this, permission);
        return state != PackageManager.PERMISSION_GRANTED;
    }

    private boolean needsRequestWritePermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Build.VERSION.SDK_INT > Build.VERSION_CODES.Q)
            return false;
        String permission = Manifest.permission.WRITE_EXTERNAL_STORAGE;
        int state = ContextCompat.checkSelfPermission(this, permission);
        return state != PackageManager.PERMISSION_GRANTED;
    }

    private void requestReadPermission() {
        String[] permissions = new String[]{Manifest.permission.READ_EXTERNAL_STORAGE};
        ActivityCompat.requestPermissions(this, permissions, MainActivity.REQUEST_LOAD_IMAGE_PERMISSION);
    }

    private void requestWritePermission() {
        String[] permissions = new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE};
        ActivityCompat.requestPermissions(this, permissions, MainActivity.REQUEST_IMAGE_CAPTURE_PERMISSION);
    }

    public int getOrientation(ContentResolver resolver, Uri uri) {
        int orientation = 0;
        try {
            Cursor cursor = resolver.query(uri,
                    new String[]{MediaStore.Images.ImageColumns.ORIENTATION},
                    null, null, null);
            if (cursor.moveToFirst())
                orientation = cursor.getInt(0);
            cursor.close();
        } catch (Exception ignore) {
            orientation = getExifOrientation(resolver, uri);
        }
        return orientation;
    }

    private int getExifOrientation(ContentResolver resolver, Uri uri) {
        int orientation = 0;
        try (InputStream in = resolver.openInputStream(uri)) {
            int orientationAttribute = (new ExifInterface(in)).getAttributeInt(ExifInterface.TAG_ORIENTATION, 0);
            orientation = Utility.convertToDegrees(orientationAttribute);
        } catch (Exception ignored) {
        }
        return orientation;
    }

    private void setMode(String modeClassName) {
        if (mEncoder.setMode(modeClassName)) {
            IModeInfo modeInfo = mEncoder.getModeInfo();
            cpvDisplayPicture.setModeSize(modeInfo.getModeSize());
            mSettings.setModeClassName(modeClassName);
        }
    }

    private void takePicture() {
        if (!hasCamera()) {
            Toast.makeText(this, "Device has no camera", Toast.LENGTH_SHORT).show();
            return;
        }
        if (needsRequestWritePermission())
            requestWritePermission();
        else
            dispatchTakePictureIntent();
    }

    private boolean hasCamera() {
        return getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY);
    }

    private void dispatchTakePictureIntent() {
        Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        Uri uri = Utility.createImageUri(this);
        if (uri != null) {
            mSettings.setImageUri(uri);
            intent.putExtra(MediaStore.EXTRA_OUTPUT, uri);
            tryToStartActivityForResult(intent, REQUEST_IMAGE_CAPTURE);
        }
    }

    private void dispatchPickPictureIntent() {
        Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        tryToStartActivityForResult(intent, REQUEST_PICK_IMAGE);
    }

    private void tryToStartActivityForResult(Intent intent, int requestCode) {
        if (intent.resolveActivity(getPackageManager()) == null) {
            Toast.makeText(this, "Another activity could not be resolved", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            startActivityForResult(intent, requestCode);
        } catch (Exception ignore) {
            Toast.makeText(this, "Another activity could not be resolved", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            case REQUEST_IMAGE_CAPTURE:
                if (resultCode == RESULT_OK) {
                    Uri uri = mSettings.getImageUri();
                    if (loadImage(uri))
                        addImageToGallery(uri);
                }
                break;
            case REQUEST_PICK_IMAGE:
                if (resultCode == RESULT_OK && data != null)
                    loadImage(data.getData());
                break;
            default:
                super.onActivityResult(requestCode, resultCode, data);
                break;
        }
    }

    private void addImageToGallery(Uri uri) {
        Intent intent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
        intent.setData(uri);
        sendBroadcast(intent);
    }

    private void showMessage(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    private void insertDataIntoDatabase(byte[] image, String encodedText) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(MyDatabaseHelper.MyEntry.COLUMN_IMAGE, image);
        if (encodedText != null) {
            String lowerCaseText = encodedText.toLowerCase();
            values.put(MyDatabaseHelper.MyEntry.COLUMN_TEXT, lowerCaseText);
        } else {
            values.put(MyDatabaseHelper.MyEntry.COLUMN_TEXT, (String) null);
        }
        SimpleDateFormat sdf = new SimpleDateFormat("h:mm a dd/MM/yyyy", Locale.getDefault());
        String timestamp = sdf.format(new Date());
        values.put(MyDatabaseHelper.MyEntry.COLUMN_TIMESTAMP, timestamp);
        db.insert(MyDatabaseHelper.MyEntry.TABLE_NAME, null, values);
        db.close();
    }

    private void displayNewData() {
        dbHelper = new MyDatabaseHelper(this);
        List<Entry> entries = dbHelper.getAllEntries();
        EntryAdapter entryAdapter = new EntryAdapter();
        entryAdapter.setEntries(entries);
        RecyclerView recyclerView = findViewById(R.id.RecyclerView);
        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        recyclerView.setLayoutManager(layoutManager);
        recyclerView.setAdapter(entryAdapter);
        if (!entries.isEmpty()) {
            recyclerView.smoothScrollToPosition(entryAdapter.getItemCount() - 1);
        }
    }

    private Runnable textEncodingRunnable;
    private final Handler mHandler = new Handler();
    private Runnable imageEncodingRunnable;

    private void startEncoding() {
        String inputText = edtEnterText.getText().toString();
        Bitmap imageBitmap = cpvDisplayPicture.getBitmap();
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        imageBitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream);
        byte[] imageByteArray = outputStream.toByteArray();
        Button btnStartEncoding = findViewById(R.id.btnStartEncoding);
        int afskSpecialFrequencyDuration = 0;
        int textEncodingDurationPerChar = 1300;
        int imageEncodingDuration = 38000;
        int totalTextEncodingTime = afskSpecialFrequencyDuration + textEncodingDurationPerChar * inputText.length();
        int totalTextAndImageEncodingTime = totalTextEncodingTime + imageEncodingDuration;
        if (!inputText.isEmpty() && cpvDisplayPicture.getVisibility() == View.GONE) {
            btnStartEncoding.setVisibility(View.INVISIBLE);
            showMessage("Encoding text now");
            AFSKEncoder.encodeAndPlayAFSKString(inputText);
            textEncodingRunnable = () -> {
                btnStartEncoding.setVisibility(View.VISIBLE);
                insertDataIntoDatabase(null, inputText);
                displayNewData();
            };
            mHandler.postDelayed(textEncodingRunnable, totalTextEncodingTime);
        } else if (inputText.isEmpty() && cpvDisplayPicture.getVisibility() == View.VISIBLE) {
            btnStartEncoding.setVisibility(View.INVISIBLE);
            btnDeletePicture.setVisibility(View.GONE);
            showMessage("Activating SSTV encoder");
            AFSKEncoder.startSSTVEncoding(1700.0);
            mEncoder.setOnEncodeCompleteListener(() -> showMessage("Encoding image now"));
            mHandler.postDelayed(() -> mEncoder.play(cpvDisplayPicture.getBitmap()), 4000);
            imageEncodingRunnable = () -> {
                showMessage("Deactivating SSTV encoder");
                AFSKEncoder.stopSSTVEncoding(1750.0);
                insertDataIntoDatabase(imageByteArray, null);
                displayNewData();
            };
            mHandler.postDelayed(imageEncodingRunnable, imageEncodingDuration + 3000);
            mHandler.postDelayed(() -> {
                btnStartEncoding.setVisibility(View.VISIBLE);
                btnDeletePicture.setVisibility(View.VISIBLE);
            }, imageEncodingDuration + 8000);
        } else if (!inputText.isEmpty() && cpvDisplayPicture.getVisibility() == View.VISIBLE) {
            btnStartEncoding.setVisibility(View.INVISIBLE);
            btnDeletePicture.setVisibility(View.GONE);
            showMessage("Encoding text now");
            AFSKEncoder.encodeAndPlayAFSKString(inputText);
            textEncodingRunnable = () -> {
                showMessage("Activating SSTV encoder");
                AFSKEncoder.startSSTVEncoding(1700.0);
                mEncoder.setOnEncodeCompleteListener(() -> showMessage("Encoding image now"));
                mHandler.postDelayed(() -> mEncoder.play(cpvDisplayPicture.getBitmap()), 4000);
                insertDataIntoDatabase(null, inputText);
                displayNewData();
            };
            mHandler.postDelayed(textEncodingRunnable, totalTextEncodingTime);
            mHandler.postDelayed(() -> {
                showMessage("Deactivating SSTV encoder");
                AFSKEncoder.stopSSTVEncoding(1750.0);
                insertDataIntoDatabase(imageByteArray, null);
                displayNewData();
            }, totalTextAndImageEncodingTime + 3000);
            mHandler.postDelayed(() -> {
                btnStartEncoding.setVisibility(View.VISIBLE);
                btnDeletePicture.setVisibility(View.VISIBLE);
            }, totalTextAndImageEncodingTime + 8000);
        } else {
            showMessage("Please enter text or add an image first");
        }
    }

    private void stopEncoding() {
        Button btnStartEncoding = findViewById(R.id.btnStartEncoding);
        btnStartEncoding.setVisibility(View.VISIBLE);
        btnDeletePicture.setVisibility(View.VISIBLE);
        AFSKEncoder.stopAudio();
        mEncoder.stop();
        mHandler.removeCallbacksAndMessages(null);
        mHandler.removeCallbacks(textEncodingRunnable);
        mHandler.removeCallbacks(imageEncodingRunnable);
    }

    private void saveWave() {
        if (Utility.isExternalStorageWritable()) {
            WaveFileOutputContext context = new WaveFileOutputContext(getContentResolver(), Utility.createWaveFileName());
            mEncoder.save(cpvDisplayPicture.getBitmap(), context);
        }
    }

    public void completeSaving(WaveFileOutputContext context) {
        context.update();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mEncoder.destroy();
    }
}