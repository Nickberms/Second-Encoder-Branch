package om.sstvencoder;

import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;

public class AFSKEncoder {
    private static volatile boolean shouldContinueAudio = true;
    private static final Object audioLock = new Object();

    public static void stopAudio() {
        synchronized (audioLock) {
            shouldContinueAudio = false;
        }
    }

    // Method to start SSTV encoding with a specified start frequency
    public static void startSSTVEncoding(double startFrequency) {
        // Create a new thread for the SSTV encoding
        Thread sstvEncodingThread = new Thread(() -> {
            // Set the flag to continue audio playback
            shouldContinueAudio = true;
            // Set the sample rate for audio playback
            int sampleRate = 44100;
            // Set the duration (in milliseconds) for the start frequency audio
            int duration = 4000;
            // Calculate the number of samples needed for the start frequency audio
            int numSamplesPerStartFrequency = (sampleRate * duration) / 1000;
            // Create an array to hold the audio samples for the start frequency
            double[] startFrequencySamples = new double[numSamplesPerStartFrequency];
            // Generate the audio samples for the start frequency
            for (int i = 0; i < numSamplesPerStartFrequency; i++) {
                double time = (double) i / sampleRate;
                startFrequencySamples[i] = Math.sin(2 * Math.PI * startFrequency * time);
            }
            // Create an array to hold all the audio samples
            double[] samples = new double[numSamplesPerStartFrequency];
            // Initialize the current index in the samples array
            int currentIndex = 0;
            // Copy the start frequency audio samples to the main samples array
            System.arraycopy(startFrequencySamples, 0, samples, currentIndex, numSamplesPerStartFrequency);
            // Play the audio with the specified start frequency
            playAudio(samples, sampleRate);
        });
        // Start the SSTV encoding thread
        sstvEncodingThread.start();
    }

    // Method to stop SSTV encoding with a specified stop frequency
    public static void stopSSTVEncoding(double stopFrequency) {
        // Create a new thread for stopping the SSTV encoding
        Thread sstvStopThread = new Thread(() -> {
            // Set the flag to continue audio playback
            shouldContinueAudio = true;
            // Set the sample rate for audio playback
            int sampleRate = 44100;
            // Set the duration (in milliseconds) for the stop frequency audio
            int duration = 4000;
            // Calculate the number of samples needed for the stop frequency audio
            int numSamplesPerStopFrequency = (sampleRate * duration) / 1000;
            // Create an array to hold the audio samples for the stop frequency
            double[] stopFrequencySamples = new double[numSamplesPerStopFrequency];
            // Generate the audio samples for the stop frequency
            for (int i = 0; i < numSamplesPerStopFrequency; i++) {
                double time = (double) i / sampleRate;
                stopFrequencySamples[i] = Math.sin(2 * Math.PI * stopFrequency * time);
            }
            // Create an array to hold all the audio samples
            double[] samples = new double[numSamplesPerStopFrequency];
            // Initialize the current index in the samples array
            int currentIndex = 0;
            // Copy the stop frequency audio samples to the main samples array
            System.arraycopy(stopFrequencySamples, 0, samples, currentIndex, numSamplesPerStopFrequency);
            // Play the audio with the specified stop frequency
            playAudio(samples, sampleRate);
        });
        // Start the SSTV stop thread
        sstvStopThread.start();
    }

    public static void encodeAndPlayAFSKString(String input) {
        Thread textEncodingThread = new Thread(() -> {
            shouldContinueAudio = true;
            int sampleRate = 44100;
            int duration = 4000;
            int pauseDuration = 500;
            int numSamplesPerBit = (sampleRate * duration) / 5000;
            int numSamplesPerPause = (sampleRate * pauseDuration) / 1000;
            int totalNumSamples = (numSamplesPerBit + numSamplesPerPause) * input.length();
            double[] samples = new double[totalNumSamples];
            int currentIndex = 0;
            for (int i = 0; i < input.length(); i++) {
                char c = input.charAt(i);
                double frequency = getUniqueFrequencyForInput(c);
                for (int j = 0; j < numSamplesPerBit; j++) {
                    double time = (double) j / sampleRate;
                    samples[currentIndex++] = Math.sin(2 * Math.PI * frequency * time);
                }
                if (i < input.length() - 1) {
                    for (int j = 0; j < numSamplesPerPause; j++) {
                        samples[currentIndex++] = 0.0;
                    }
                }
            }
            playAudio(samples, sampleRate);
        });
        textEncodingThread.start();
    }

    // USE THIS encodeAndPlayAFSKString METHOD INSTEAD IF THE DEFAULT LISTENER IS THE SSTV
    /*
    public static void encodeAndPlayAFSKString(String input) {
        Thread textEncodingThread = new Thread(() -> {
            shouldContinueAudio = true;
            int sampleRate = 44100;
            int duration = 4000;
            int pauseDuration = 500;
            int numSamplesPerBit = (sampleRate * duration) / 5000;
            int numSamplesPerPause = (sampleRate * pauseDuration) / 1000;
            double startFrequency = 1800.0;
            int numSamplesPerStartFrequency = (sampleRate * duration) / 1000;
            double[] startFrequencySamples = new double[numSamplesPerStartFrequency];
            for (int i = 0; i < numSamplesPerStartFrequency; i++) {
                double time = (double) i / sampleRate;
                startFrequencySamples[i] = Math.sin(2 * Math.PI * startFrequency * time);
            }
            double stopFrequency = 1850.0;
            int numSamplesPerStopFrequency = (sampleRate * duration) / 1000;
            double[] stopFrequencySamples = new double[numSamplesPerStopFrequency];
            for (int i = 0; i < numSamplesPerStopFrequency; i++) {
                double time = (double) i / sampleRate;
                stopFrequencySamples[i] = Math.sin(2 * Math.PI * stopFrequency * time);
            }
            int totalNumSamples = (numSamplesPerBit + numSamplesPerPause) * input.length() +
                    numSamplesPerStartFrequency + numSamplesPerStopFrequency;
            double[] samples = new double[totalNumSamples];
            int currentIndex = 0;
            System.arraycopy(startFrequencySamples, 0, samples, currentIndex, numSamplesPerStartFrequency);
            currentIndex += numSamplesPerStartFrequency;
            for (int i = 0; i < input.length(); i++) {
                char c = input.charAt(i);
                double frequency = getUniqueFrequencyForInput(c);
                for (int j = 0; j < numSamplesPerBit; j++) {
                    double time = (double) j / sampleRate;
                    samples[currentIndex++] = Math.sin(2 * Math.PI * frequency * time);
                }
                if (i < input.length() - 1) {
                    for (int j = 0; j < numSamplesPerPause; j++) {
                        samples[currentIndex++] = 0.0;
                    }
                }
            }
            System.arraycopy(stopFrequencySamples, 0, samples, currentIndex, numSamplesPerStopFrequency);
            playAudio(samples, sampleRate);
        });
        textEncodingThread.start();
    }
    */

    private static void playAudio(final double[] samples, final int sampleRate) {
        AudioTrack audioTrack = new AudioTrack(AudioManager.STREAM_MUSIC,
                sampleRate, AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT, samples.length * 2,
                AudioTrack.MODE_STATIC);
        audioTrack.write(shortArrayFromDoubleArray(samples), 0, samples.length);
        Thread audioThread = new Thread(() -> {
            audioTrack.play();
            while (shouldContinueAudio && audioTrack.getPlaybackHeadPosition() < samples.length) {
                try {
                    Thread.sleep(50);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
            audioTrack.stop();
            audioTrack.release();
        });
        audioThread.start();
    }

    private static double getUniqueFrequencyForInput(char c) {
        switch (Character.toLowerCase(c)) {
            case '0':
                return 2000.0;
            case '1':
                return 2050.0;
            case '2':
                return 2100.0;
            case '3':
                return 2150.0;
            case '4':
                return 2200.0;
            case '5':
                return 2250.0;
            case '6':
                return 2300.0;
            case '7':
                return 2350.0;
            case '8':
                return 2400.0;
            case '9':
                return 2450.0;
            case '/':
                return 2500.0;
            case '=':
                return 2550.0;
            case ':':
                return 2600.0;
            case '?':
                return 2650.0;
            case ',':
                return 2700.0;
            case ' ':
                return 2750.0;
            case '.':
                return 2800.0;
            case 'a':
                return 2850.0;
            case 'b':
                return 2900.0;
            case 'c':
                return 2950.0;
            case 'd':
                return 3000.0;
            case 'e':
                return 3050.0;
            case 'f':
                return 3100.0;
            case 'g':
                return 3150.0;
            case 'h':
                return 3200.0;
            case 'i':
                return 3250.0;
            case 'j':
                return 3300.0;
            case 'k':
                return 3350.0;
            case 'l':
                return 3400.0;
            case 'm':
                return 3450.0;
            case 'n':
                return 3500.0;
            case 'o':
                return 3550.0;
            case 'p':
                return 3600.0;
            case 'q':
                return 3650.0;
            case 'r':
                return 3700.0;
            case 's':
                return 3750.0;
            case 't':
                return 3800.0;
            case 'u':
                return 3850.0;
            case 'v':
                return 3900.0;
            case 'w':
                return 3950.0;
            case 'x':
                return 4000.0;
            case 'y':
                return 4050.0;
            case 'z':
                return 4100.0;
        }
        return 0;
    }

    private static short[] shortArrayFromDoubleArray(double[] doubles) {
        short[] shorts = new short[doubles.length];
        for (int i = 0; i < doubles.length; i++) {
            shorts[i] = (short) (doubles[i] * Short.MAX_VALUE);
        }
        return shorts;
    }
}