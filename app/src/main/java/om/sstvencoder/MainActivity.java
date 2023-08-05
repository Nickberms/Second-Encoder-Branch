package om.sstvencoder;

import android.Manifest;
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
    private CropView mCropView;
    private Encoder mEncoder;
    private EditText inputEditText;
    private Button deleteButton;
    private MyDatabaseHelper dbHelper;
    private LocationHelper locationHelper;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        // Get the file path of the database and log it
        File databaseFile = getApplicationContext().getDatabasePath("encoder_database.db");
        Log.d("MainActivity", "Database file path: " + databaseFile.getAbsolutePath());
        // Find views from layout
        mCropView = findViewById(R.id.cropView);
        deleteButton = findViewById(R.id.deleteButton);
        // Set initial visibility for the CropView and DeleteButton
        mCropView.setVisibility(View.GONE);
        deleteButton.setVisibility(View.GONE);
        // Check if the app is restarted
        boolean isRestarted = isAppRestarted();
        if (!isRestarted) {
            // If not restarted, hide CropView and destroy previous data
            mCropView.setVisibility(View.GONE);
            destroyPreviousData();
        }
        // Create an instance of MainActivityMessenger for communication with the encoder
        MainActivityMessenger messenger = new MainActivityMessenger(this);
        // Create an instance of Encoder for AFSK and SSTV encoding
        mEncoder = new Encoder(messenger);
        // Create an instance of Settings for managing app settings
        mSettings = new Settings(this);
        // Load the app settings
        mSettings.load();
        // Set the encoding mode based on the saved settings
        mEncoder = new Encoder(messenger);
        setMode(mSettings.getModeClassName());
        if (!isRestarted) {
            // If not restarted, show the delete button and load the image from intent
            deleteButton.setVisibility(View.VISIBLE);
            loadImage(getIntent());
        }
        // Find the "Play" button and set a click listener to start encoding
        Button playButton = findViewById(R.id.action_play);
        playButton.setOnClickListener(v -> startEncoding());
        // Find the "Stop" button and set a click listener to stop encoding
        Button stop = findViewById(R.id.btnStop);
        stop.setOnClickListener(v -> stopEncoding());
        // Find the "Pick Image" button and set a click listener to pick an image from the gallery
        Button pickImageButton = findViewById(R.id.btnPickPicture);
        pickImageButton.setOnClickListener(v -> dispatchPickPictureIntent());
        // Find the "Capture Image" button and set a click listener to capture an image using the camera
        Button imageCaptureButton = findViewById(R.id.btnTakePicture);
        imageCaptureButton.setOnClickListener(v -> takePicture());
        // Find the input text EditText
        inputEditText = findViewById(R.id.inputEditText);
        // Initialize Stetho for debugging database
        Stetho.initializeWithDefaults(this);
        // Display the existing data in the RecyclerView
        displayNewData();
        // Find the "Location" button and set a click listener to get location and update text field
        Button btnLocation = findViewById(R.id.btnLocation);
        btnLocation.setOnClickListener(v -> getLocationAndUpdateTextField());
        // Check if location permission is granted
        if (checkLocationPermission()) {
            showMessage("Location permission successfully granted.");
        } else {
            // Request location permission if not granted
            requestLocationPermission();
        }
    }

    private void getLocationAndUpdateTextField() {
        if (checkLocationPermission()) {
            LocationManager locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
            if (locationManager != null && !locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                showMessage("Please turn on the device location.");
            } else {
                if (locationHelper == null) {
                    locationHelper = new LocationHelper(this, inputEditText);
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
                    showMessage("Location permission successfully granted.");
                } else {
                    showMessage("Location permission not granted.");
                    int delayMillis = 3000;
                    new Handler().postDelayed(() -> showMessage("You can grant the app location permission in Settings."), delayMillis);
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
            mCropView.setVisibility(View.GONE);
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        mCropView.setVisibility(View.GONE);
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
        mCropView.setVisibility(View.GONE);
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
        deleteButton.setVisibility(View.GONE);
        if (uri != null) {
            mCropView.setVisibility(View.VISIBLE);
            try {
                InputStream stream = resolver.openInputStream(uri);
                if (stream != null) {
                    mCropView.setBitmap(stream);
                    succeeded = true;
                    deleteButton.setVisibility(View.VISIBLE);
                    deleteButton.setOnClickListener(v -> resetState());
                }
            } catch (Exception ex) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && isPermissionException(ex)
                        && needsRequestReadPermission()) {
                    requestReadPermission();
                }
            }
        } else {
            mCropView.setVisibility(View.GONE);
        }
        if (succeeded) {
            mCropView.rotateImage(getOrientation(resolver, uri));
            mSettings.setImageUri(uri);
        }
        return succeeded;
    }

    private void resetState() {
        try {
            mCropView.setBitmap(null);
            mSettings.setImageUri(null);
        } catch (Exception e) {
            e.printStackTrace();
        }
        deleteButton.setVisibility(View.GONE);
        mCropView.setVisibility(View.GONE);
        mSettings.setImageUri(null);
    }

    private void setDefaultBitmap() {
        try {
            mCropView.setBitmap(getResources().openRawResource(R.raw.smpte_color_bars));
        } catch (Exception ignore) {
            mCropView.setNoBitmap();
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
            mCropView.setModeSize(modeInfo.getModeSize());
            mSettings.setModeClassName(modeClassName);
        }
    }

    private void takePicture() {
        if (!hasCamera()) {
            Toast.makeText(this, getString(R.string.message_no_camera), Toast.LENGTH_LONG).show();
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
            Toast.makeText(this, R.string.another_activity_resolve_err, Toast.LENGTH_LONG).show();
            return;
        }
        try {
            startActivityForResult(intent, requestCode);
        } catch (Exception ignore) {
            Toast.makeText(this, R.string.another_activity_start_err, Toast.LENGTH_LONG).show();
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

    // Method to insert data into the database
    private void insertDataIntoDatabase(byte[] image, String encodedText) {
        // Get a writable instance of the database using the database helper
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        // Create a ContentValues object to hold the data to be inserted
        ContentValues values = new ContentValues();
        // Put the image data into the ContentValues object, using the "COLUMN_IMAGE" as the key
        values.put(MyDatabaseHelper.MyEntry.COLUMN_IMAGE, image);
        // Put the encoded text data into the ContentValues object, using the "COLUMN_TEXT" as the key
        values.put(MyDatabaseHelper.MyEntry.COLUMN_TEXT, encodedText);
        // Get the current timestamp in the format "h:mm a dd/MM/yyyy"
        SimpleDateFormat sdf = new SimpleDateFormat("h:mm a dd/MM/yyyy", Locale.getDefault());
        String timestamp = sdf.format(new Date());
        // Put the timestamp data into the ContentValues object, using the "COLUMN_TIMESTAMP" as the key
        values.put(MyDatabaseHelper.MyEntry.COLUMN_TIMESTAMP, timestamp);
        // Insert the data into the database table using the "insert" method
        // The "null" parameter means that this is a new row and it doesn't replace any existing row
        db.insert(MyDatabaseHelper.MyEntry.TABLE_NAME, null, values);
        // Close the database to release resources
        db.close();
    }

    // Method to display new data in the RecyclerView
    private void displayNewData() {
        // Create a new instance of the database helper
        dbHelper = new MyDatabaseHelper(this);
        // Get all entries from the database using the database helper
        List<Entry> entries = dbHelper.getAllEntries();
        // Create a new instance of the EntryAdapter
        EntryAdapter entryAdapter = new EntryAdapter();
        // Set the list of entries in the EntryAdapter
        entryAdapter.setEntries(entries);
        // Get the RecyclerView from the layout
        RecyclerView recyclerView = findViewById(R.id.recyclerView);
        // Create a new LinearLayoutManager for the RecyclerView
        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        // Set the layout manager for the RecyclerView
        recyclerView.setLayoutManager(layoutManager);
        // Set the EntryAdapter for the RecyclerView
        recyclerView.setAdapter(entryAdapter);
    }

    // Runnable to handle text encoding
    private Runnable textEncodingRunnable;
    // Handler to manage delayed tasks
    private final Handler mHandler = new Handler();
    // Runnable to handle image encoding
    private Runnable imageEncodingRunnable;

    // Method to start the encoding process
    private void startEncoding() {
        // Get the input text from the EditText
        String inputText = inputEditText.getText().toString();
        // Get the image bitmap from the CropView
        Bitmap imageBitmap = mCropView.getBitmap();
        // Compress the image to a PNG format and convert to a byte array
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        imageBitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream);
        byte[] imageByteArray = outputStream.toByteArray();
        // Get the "Play" button from the layout
        Button playButton = findViewById(R.id.action_play);
        // Duration of the special frequency for AFSK encoding (if used)
        int afskSpecialFrequencyDuration = 0;
        // Duration of encoding each character in the input text
        int textEncodingDurationPerChar = 1300;
        // Duration of encoding the image
        int imageEncodingDuration = 38000;
        // Calculate the total duration of encoding only the text
        int totalTextEncodingTime = afskSpecialFrequencyDuration + textEncodingDurationPerChar * inputText.length();
        // Calculate the total duration of encoding both the text and the image
        int totalTextAndImageEncodingTime = totalTextEncodingTime + imageEncodingDuration;
        // Check if input text is not empty and the CropView is hidden (image not used)
        if (!inputText.isEmpty() && mCropView.getVisibility() == View.GONE) {
            playButton.setVisibility(View.INVISIBLE);
            showMessage("Encoding text now.");
            // Start AFSK encoding for the input text
            AFSKEncoder.encodeAndPlayAFSKString(inputText);
            // Runnable to be executed after the total text encoding time
            textEncodingRunnable = () -> {
                // Insert data into the database with no image (null)
                insertDataIntoDatabase(null, inputText);
                // Make the "Play" button visible again
                playButton.setVisibility(View.VISIBLE);
                // Display the new data in the RecyclerView
                displayNewData();
            };
            // Schedule the text encoding runnable to be executed after the total text encoding time
            mHandler.postDelayed(textEncodingRunnable, totalTextEncodingTime);
        }
        // Check if input text is empty and the CropView is visible (image used)
        else if (inputText.isEmpty() && mCropView.getVisibility() == View.VISIBLE) {
            playButton.setVisibility(View.INVISIBLE);
            showMessage("Activating SSTV encoder.");
            // Start AFSK encoding with the special start frequency for SSTV
            AFSKEncoder.startSSTVEncoding(1900.0);
            // Set a listener to show a message when image encoding starts
            mEncoder.setOnEncodeCompleteListener(() -> showMessage("Encoding image now."));
            // Schedule the image encoding to start after a short delay (4000 milliseconds)
            mHandler.postDelayed(() -> mEncoder.play(mCropView.getBitmap()), 4000);
            // Runnable to be executed after the total image encoding time
            imageEncodingRunnable = () -> {
                showMessage("Deactivating SSTV encoder.");
                // Stop SSTV encoding with the special stop frequency for SSTV
                AFSKEncoder.stopSSTVEncoding(1950.0);
                // Insert data into the database with the image and no text (null)
                insertDataIntoDatabase(imageByteArray, null);
                // Display the new data in the RecyclerView
                displayNewData();
            };
            // Schedule the image encoding runnable to be executed after the total image encoding time plus a delay (3000 milliseconds)
            mHandler.postDelayed(imageEncodingRunnable, imageEncodingDuration + 3000);
            // Schedule to make the "Play" button visible again after the total image encoding time plus a longer delay (8000 milliseconds)
            mHandler.postDelayed(() -> playButton.setVisibility(View.VISIBLE), imageEncodingDuration + 8000);
        }
        // Check if input text is not empty and the CropView is visible (image used)
        else if (!inputText.isEmpty() && mCropView.getVisibility() == View.VISIBLE) {
            playButton.setVisibility(View.INVISIBLE);
            showMessage("Encoding text now.");
            // Start AFSK encoding for the input text
            AFSKEncoder.encodeAndPlayAFSKString(inputText);
            // Runnable to be executed after the total text encoding time
            textEncodingRunnable = () -> {
                showMessage("Activating SSTV encoder.");
                // Start AFSK encoding with the special start frequency for SSTV
                AFSKEncoder.startSSTVEncoding(1900.0);
                // Set a listener to show a message when image encoding starts
                mEncoder.setOnEncodeCompleteListener(() -> showMessage("Encoding image now."));
                // Schedule the image encoding to start after a short delay (4000 milliseconds)
                mHandler.postDelayed(() -> mEncoder.play(mCropView.getBitmap()), 4000);
            };
            // Schedule the text encoding runnable to be executed after the total text encoding time
            mHandler.postDelayed(textEncodingRunnable, totalTextEncodingTime);
            // Runnable to be executed after the total text and image encoding time
            mHandler.postDelayed(() -> {
                showMessage("Deactivating SSTV encoder.");
                // Stop SSTV encoding with the special stop frequency for SSTV
                AFSKEncoder.stopSSTVEncoding(1950.0);
                // Insert data into the database with the image and text
                insertDataIntoDatabase(imageByteArray, inputText);
                // Display the new data in the RecyclerView
                displayNewData();
            }, totalTextAndImageEncodingTime + 3000);
            // Schedule to make the "Play" button visible again after the total text and image encoding time plus a longer delay (8000 milliseconds)
            mHandler.postDelayed(() -> playButton.setVisibility(View.VISIBLE), totalTextAndImageEncodingTime + 8000);
        } else {
            showMessage("Please enter text or add an image first.");
        }
    }

    // Method to stop the encoding process
    private void stopEncoding() {
        // Remove all callbacks and messages from the handler (cancel scheduled tasks)
        mHandler.removeCallbacksAndMessages(null);
        // Stop the encoder
        mEncoder.stop();
        // Stop the AFSK audio
        AFSKEncoder.stopAudio();
        // Get the "Play" button from the layout
        Button playButton = findViewById(R.id.action_play);
        // Make the "Play" button visible again
        playButton.setVisibility(View.VISIBLE);
        // Remove the text encoding runnable
        mHandler.removeCallbacks(textEncodingRunnable);
        // Remove the image encoding runnable
        mHandler.removeCallbacks(imageEncodingRunnable);
    }

    private void saveWave() {
        if (Utility.isExternalStorageWritable()) {
            WaveFileOutputContext context = new WaveFileOutputContext(getContentResolver(), Utility.createWaveFileName());
            mEncoder.save(mCropView.getBitmap(), context);
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