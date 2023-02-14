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
	private final int outputCount = 16;

	private native void configureEncoder(byte[] payload);

	private native boolean drawEncoder(float[] audioBuffer, int sampleCount);

	private native boolean produceEncoder();

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
			produceEncoder();
			boolean empty = drawEncoder(outputBuffer, outputBuffer.length);
			audioTrack.write(outputBuffer, 0, outputBuffer.length, AudioTrack.WRITE_BLOCKING);
			if (empty)
				audioTrack.stop();
		}
	};

	private void initAudioTrack() {
		int sampleSize = 4;
		int channelCount = 1;
		int bufferSize = outputCount * outputBuffer.length * sampleSize * channelCount;
		audioTrack = new AudioTrack(AudioManager.STREAM_MUSIC, 8000, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_FLOAT, bufferSize, AudioTrack.MODE_STREAM);
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
			configureEncoder(payload);
			outputBuffer = new float[256];
			initAudioTrack();
			handler.postDelayed(this::transmit, 1000);
		}
	}

	private void transmit() {
		for (int i = 0; i < outputCount; ++i) {
			produceEncoder();
			drawEncoder(outputBuffer, outputBuffer.length);
			audioTrack.write(outputBuffer, 0, outputBuffer.length, AudioTrack.WRITE_BLOCKING);
		}
		audioTrack.play();
	}
}