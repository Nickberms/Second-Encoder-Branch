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

    public static void startSSTVEncoding(double startFrequency) {
        Thread sstvEncodingThread = new Thread(() -> {
            shouldContinueAudio = true;
            int sampleRate = 44100;
            int duration = 4000;
            int numSamplesPerStartFrequency = (sampleRate * duration) / 1000;
            double[] startFrequencySamples = new double[numSamplesPerStartFrequency];
            for (int i = 0; i < numSamplesPerStartFrequency; i++) {
                double time = (double) i / sampleRate;
                startFrequencySamples[i] = Math.sin(2 * Math.PI * startFrequency * time);
            }
            double[] samples = new double[numSamplesPerStartFrequency];
            int currentIndex = 0;
            System.arraycopy(startFrequencySamples, 0, samples, currentIndex, numSamplesPerStartFrequency);
            playAudio(samples, sampleRate);
        });
        sstvEncodingThread.start();
    }

    // USE THIS stopSSTVEncoding METHOD IF YOU WANT TO USE A SPECIAL FREQUENCY TO SIGNAL THE END OF SSTV ENCODING
    /*
    public static void stopSSTVEncoding(double stopFrequency) {
        Thread sstvStopThread = new Thread(() -> {
            shouldContinueAudio = true;
            int sampleRate = 44100;
            int duration = 4000;
            int numSamplesPerStopFrequency = (sampleRate * duration) / 1000;
            double[] stopFrequencySamples = new double[numSamplesPerStopFrequency];
            for (int i = 0; i < numSamplesPerStopFrequency; i++) {
                double time = (double) i / sampleRate;
                stopFrequencySamples[i] = Math.sin(2 * Math.PI * stopFrequency * time);
            }
            double[] samples = new double[numSamplesPerStopFrequency];
            int currentIndex = 0;
            System.arraycopy(stopFrequencySamples, 0, samples, currentIndex, numSamplesPerStopFrequency);
            playAudio(samples, sampleRate);
        });
        sstvStopThread.start();
    }
    */

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

            // FREQUENCY FOR NUMBERS
            case '0':
                return 2000.0;
            case '1':
                return 2100.0;
            case '2':
                return 2200.0;
            case '3':
                return 2300.0;
            case '4':
                return 2400.0;
            case '5':
                return 2500.0;
            case '6':
                return 2600.0;
            case '7':
                return 2700.0;
            case '8':
                return 2800.0;
            case '9':
                return 2900.0;

            // FREQUENCY FOR LETTERS
            case 'a':
                return 3000.0;
            case 'b':
                return 3100.0;
            case 'c':
                return 3200.0;
            case 'd':
                return 3300.0;
            case 'e':
                return 3400.0;
            case 'f':
                return 3500.0;
            case 'g':
                return 3600.0;
            case 'h':
                return 3700.0;
            case 'i':
                return 3800.0;
            case 'j':
                return 3900.0;
            case 'k':
                return 4000.0;
            case 'l':
                return 4100.0;
            case 'm':
                return 4200.0;
            case 'n':
                return 4300.0;
            case 'o':
                return 4400.0;
            case 'p':
                return 4500.0;
            case 'q':
                return 4600.0;
            case 'r':
                return 4700.0;
            case 's':
                return 4800.0;
            case 't':
                return 4900.0;
            case 'u':
                return 5000.0;
            case 'v':
                return 5100.0;
            case 'w':
                return 5200.0;
            case 'x':
                return 5300.0;
            case 'y':
                return 5400.0;
            case 'z':
                return 5500.0;

            // FREQUENCY FOR SPECIAL CHARACTERS
            case '!':
                return 5600.0;
            case '?':
                return 5700.0;
            case ',':
                return 5800.0;
            case '.':
                return 5900.0;
            case ' ':
                return 6000.0;
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