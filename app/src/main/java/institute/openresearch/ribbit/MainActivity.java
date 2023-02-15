package institute.openresearch.ribbit;

import androidx.appcompat.app.AppCompatActivity;

import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.os.Bundle;
import android.os.Handler;
import android.widget.TextView;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import institute.openresearch.ribbit.databinding.ActivityMainBinding;

public class MainActivity extends AppCompatActivity {

	// Used to load the 'ribbit' library on application startup.
	static {
		System.loadLibrary("ribbit");
	}

	private ActivityMainBinding binding;
	private AudioTrack audioTrack;
	private Handler handler;
	private float[] outputBuffer;
	private int outputCount;

	private native void initEncoder(byte[] payload);

	private native boolean readEncoder(float[] audioBuffer, int sampleCount);

	private native boolean createEncoder();

	private native void destroyEncoder();

	private native boolean fetchDecoder(byte[] payload);

	private native boolean feedDecoder(float[] audioBuffer, int sampleCount);

	private native boolean processDecoder();

	private native boolean createDecoder();

	private native void destroyDecoder();

	private final AudioTrack.OnPlaybackPositionUpdateListener outputListener = new AudioTrack.OnPlaybackPositionUpdateListener() {
		@Override
		public void onMarkerReached(AudioTrack ignore) {

		}

		@Override
		public void onPeriodicNotification(AudioTrack audioTrack) {
			boolean done = readEncoder(outputBuffer, outputBuffer.length);
			audioTrack.write(outputBuffer, 0, outputBuffer.length, AudioTrack.WRITE_BLOCKING);
			if (done)
				audioTrack.stop();
		}
	};

	private void initAudioTrack() {
		int sampleRate = 8000;
		int sampleSize = 4;
		int channelCount = 1;
		int writesPerSecond = 50;
		double bufferSeconds = 0.5;
		outputBuffer = new float[(sampleRate * channelCount) / writesPerSecond];
		outputCount = (int)(sampleRate * channelCount * bufferSeconds) / outputBuffer.length;
		int bufferSize = outputCount * outputBuffer.length * sampleSize;
		audioTrack = new AudioTrack(AudioManager.STREAM_MUSIC, sampleRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_FLOAT, bufferSize, AudioTrack.MODE_STREAM);
		audioTrack.setPlaybackPositionUpdateListener(outputListener);
		audioTrack.setPositionNotificationPeriod(outputBuffer.length);
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		binding = ActivityMainBinding.inflate(getLayoutInflater());
		setContentView(binding.getRoot());
		handler = new Handler(getMainLooper());

		// Example of a call to a native method
		TextView tv = binding.sampleText;
		if (!createEncoder()) {
			tv.setText("failed creating encoder");
		} else if (!createDecoder()) {
			tv.setText("failed creating decoder");
		} else {
			tv.setText("ready to go");
			String message = "Hello World!\n";
			byte[] payload = Arrays.copyOf(message.getBytes(StandardCharsets.UTF_8), 256);
			initEncoder(payload);
			initAudioTrack();
			handler.postDelayed(this::transmit, 1000);
		}
	}

	private void transmit() {
		for (int i = 0; i < outputCount; ++i) {
			readEncoder(outputBuffer, outputBuffer.length);
			audioTrack.write(outputBuffer, 0, outputBuffer.length, AudioTrack.WRITE_BLOCKING);
		}
		audioTrack.play();
	}
}