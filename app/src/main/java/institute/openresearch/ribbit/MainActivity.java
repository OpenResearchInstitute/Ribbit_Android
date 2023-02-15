package institute.openresearch.ribbit;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.os.Handler;
import android.widget.TextView;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import institute.openresearch.ribbit.databinding.ActivityMainBinding;

public class MainActivity extends AppCompatActivity {

	// Used to load the 'ribbit' library on application startup.
	static {
		System.loadLibrary("ribbit");
	}

	private final int permissionID = 1;
	private ActivityMainBinding binding;
	private AudioTrack audioTrack;
	private AudioRecord audioRecord;
	private Handler handler;
	private float[] recordBuffer;
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
			if (done) {
				audioTrack.stop();
				handler.postDelayed(() -> startListening(), 1000);
			}
		}
	};

	private void initAudioTrack() {
		int sampleRate = 8000;
		int sampleSize = 4;
		int channelCount = 1;
		int writesPerSecond = 50;
		double bufferSeconds = 0.5;
		outputBuffer = new float[(sampleRate * channelCount) / writesPerSecond];
		outputCount = (int) (sampleRate * channelCount * bufferSeconds) / outputBuffer.length;
		int bufferSize = outputCount * outputBuffer.length * sampleSize;
		audioTrack = new AudioTrack(AudioManager.STREAM_MUSIC, sampleRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_FLOAT, bufferSize, AudioTrack.MODE_STREAM);
		audioTrack.setPlaybackPositionUpdateListener(outputListener);
		audioTrack.setPositionNotificationPeriod(outputBuffer.length);
	}

	private final AudioRecord.OnRecordPositionUpdateListener recordListener = new AudioRecord.OnRecordPositionUpdateListener() {
		@Override
		public void onMarkerReached(AudioRecord ignore) {
		}

		@Override
		public void onPeriodicNotification(AudioRecord audioRecord) {
			audioRecord.read(recordBuffer, 0, recordBuffer.length, AudioRecord.READ_BLOCKING);
			if (feedDecoder(recordBuffer, recordBuffer.length) && processDecoder()) {
				byte[] payload = new byte[256];
				if (fetchDecoder(payload))
					binding.sampleText.setText(new String(payload).trim());
				else
					binding.sampleText.setText("payload decoding error");
			}
		}
	};

	private void initAudioRecord() {
		int sampleRate = 8000;
		int sampleSize = 4;
		int channelCount = 1;
		int readsPerSecond = 50;
		double bufferSeconds = 2;
		recordBuffer = new float[(sampleRate * channelCount) / readsPerSecond];
		int recordCount = (int) (sampleRate * channelCount * bufferSeconds) / recordBuffer.length;
		int bufferSize = recordCount * recordBuffer.length * sampleSize;
		try {
			audioRecord = new AudioRecord(MediaRecorder.AudioSource.DEFAULT, sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_FLOAT, bufferSize);
			if (audioRecord.getState() == AudioRecord.STATE_INITIALIZED) {
				audioRecord.setRecordPositionUpdateListener(recordListener);
				audioRecord.setPositionNotificationPeriod(recordBuffer.length);
				startListening();
			} else {
				binding.sampleText.setText("audio init failed");
			}
		} catch (IllegalArgumentException e) {
			binding.sampleText.setText("audio setup failed");
		} catch (SecurityException e) {
			binding.sampleText.setText("audio permission denied");
		}
	}

	private void startListening() {
		if (audioRecord != null) {
			audioRecord.startRecording();
			if (audioRecord.getRecordingState() == AudioRecord.RECORDSTATE_RECORDING) {
				audioRecord.read(recordBuffer, 0, recordBuffer.length, AudioRecord.READ_BLOCKING);
				binding.sampleText.setText("listening");
			} else {
				binding.sampleText.setText("audio recording error");
			}
		}
	}

	private void stopListening() {
		if (audioRecord != null)
			audioRecord.stop();
	}

	@Override
	public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
		super.onRequestPermissionsResult(requestCode, permissions, grantResults);
		if (requestCode != permissionID)
			return;
		for (int i = 0; i < permissions.length; ++i)
			if (permissions[i].equals(Manifest.permission.RECORD_AUDIO) && grantResults[i] == PackageManager.PERMISSION_GRANTED)
				initAudioRecord();
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
		}
		initAudioTrack();
		List<String> permissions = new ArrayList<>();
		if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
			permissions.add(Manifest.permission.RECORD_AUDIO);
			tv.setText("audio permission denied");
		} else {
			initAudioRecord();
		}
		if (!permissions.isEmpty())
			ActivityCompat.requestPermissions(this, permissions.toArray(new String[0]), permissionID);
		if (false) {
			String message = "Hello World!\n";
			byte[] payload = Arrays.copyOf(message.getBytes(StandardCharsets.UTF_8), 256);
			initEncoder(payload);
			handler.postDelayed(this::transmit, 3000);
		}
	}

	private void transmit() {
		stopListening();
		for (int i = 0; i < outputCount; ++i) {
			readEncoder(outputBuffer, outputBuffer.length);
			audioTrack.write(outputBuffer, 0, outputBuffer.length, AudioTrack.WRITE_BLOCKING);
		}
		binding.sampleText.setText("transmitting");
		audioTrack.play();
	}
}