package institute.openresearch.ribbit;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
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
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
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
	private String message;

	private native void initEncoder(byte[] payload);

	private native boolean readEncoder(float[] audioBuffer, int sampleCount);

	private native boolean createEncoder();

	private native void destroyEncoder();

	private native boolean fetchDecoder(byte[] payload);

	private native boolean feedDecoder(float[] audioBuffer, int sampleCount);

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
			if (feedDecoder(recordBuffer, recordBuffer.length)) {
				byte[] payload = new byte[256];
				if (fetchDecoder(payload))
					binding.status.setText(new String(payload).trim());
				else
					binding.status.setText(R.string.payload_decoding_error);
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
				binding.status.setText(R.string.audio_init_failed);
			}
		} catch (IllegalArgumentException e) {
			binding.status.setText(R.string.audio_setup_failed);
		} catch (SecurityException e) {
			binding.status.setText(R.string.audio_permission_denied);
		}
	}

	private void startListening() {
		if (audioRecord != null) {
			audioRecord.startRecording();
			if (audioRecord.getRecordingState() == AudioRecord.RECORDSTATE_RECORDING) {
				audioRecord.read(recordBuffer, 0, recordBuffer.length, AudioRecord.READ_BLOCKING);
				binding.status.setText(R.string.listening);
			} else {
				binding.status.setText(R.string.audio_recording_error);
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
		message = "Hello World!";

		if (!createEncoder())
			binding.status.setText(R.string.failed_creating_encoder);
		else if (!createDecoder())
			binding.status.setText(R.string.failed_creating_decoder);
		initAudioTrack();
		List<String> permissions = new ArrayList<>();
		if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
			permissions.add(Manifest.permission.RECORD_AUDIO);
			binding.status.setText(R.string.audio_permission_denied);
		} else {
			initAudioRecord();
		}
		if (!permissions.isEmpty())
			ActivityCompat.requestPermissions(this, permissions.toArray(new String[0]), permissionID);
	}

	private void transmit() {
		byte[] payload = Arrays.copyOf(message.getBytes(StandardCharsets.UTF_8), 256);
		initEncoder(payload);
		stopListening();
		for (int i = 0; i < outputCount; ++i) {
			readEncoder(outputBuffer, outputBuffer.length);
			audioTrack.write(outputBuffer, 0, outputBuffer.length, AudioTrack.WRITE_BLOCKING);
		}
		binding.status.setText(R.string.transmitting);
		audioTrack.play();
	}

	private void composeMessage() {
		View view = getLayoutInflater().inflate(R.layout.composer, null);
		EditText edit = view.findViewById(R.id.message);
		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		builder.setTitle(R.string.compose_message);
		builder.setView(view);
		builder.setNeutralButton(R.string.back, (dialogInterface, i) -> message = edit.getText().toString());
		builder.setPositiveButton(R.string.transmit, (dialogInterface, i) -> {
			message = edit.getText().toString();
			transmit();
		});
		builder.setOnCancelListener(dialogInterface -> message = edit.getText().toString());
		AlertDialog dialog = builder.show();
		TextView left = view.findViewById(R.id.capacity);
		edit.addTextChangedListener(new TextWatcher() {
			@Override
			public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {

			}

			@Override
			public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
				int bytes = charSequence.toString().getBytes().length;
				if (bytes <= 256) {
					int num = 256 - bytes;
					left.setText(getResources().getQuantityString(R.plurals.bytes_left, num, num));
					dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(true);
				} else {
					int num = bytes - 256;
					left.setText(getResources().getQuantityString(R.plurals.over_capacity, num, num));
					dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);
				}
			}

			@Override
			public void afterTextChanged(Editable editable) {

			}
		});
		edit.setText(message);
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		getMenuInflater().inflate(R.menu.menu_main, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		int id = item.getItemId();
		if (id == R.id.action_compose) {
			composeMessage();
			return true;
		}
		return super.onOptionsItemSelected(item);
	}

	@Override
	protected void onResume() {
		startListening();
		super.onResume();
	}

	@Override
	protected void onPause() {
		stopListening();
		super.onPause();
	}

	@Override
	protected void onDestroy() {
		audioTrack.stop();
		destroyEncoder();
		destroyDecoder();
		super.onDestroy();
	}
}