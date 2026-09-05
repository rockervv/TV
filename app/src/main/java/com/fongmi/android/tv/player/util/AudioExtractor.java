package com.fongmi.android.tv.player.util;

import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.util.Map;

public class AudioExtractor {

    private static final String TAG = "AudioExtractor";
    private static final long TIMEOUT_US = 10000;
    private static final long MAX_DURATION_US = 5000000; // 5 seconds

    public interface Callback {
        void onSuccess(byte[] pcmData);
        void onError(Exception e);
    }

    public static void extract(String url, Map<String, String> headers, Callback callback) {
        new Thread(() -> {
            try {
                byte[] pcmData = extractSync(url, headers);
                if (pcmData != null && pcmData.length > 0) {
                    callback.onSuccess(pcmData);
                } else {
                    callback.onError(new Exception("Failed to extract PCM data"));
                }
            } catch (Exception e) {
                callback.onError(e);
            }
        }).start();
    }

    public static byte[] extractSync(String url, Map<String, String> headers) throws Exception {
        MediaExtractor extractor = new MediaExtractor();
        extractor.setDataSource(url, headers);

        int audioTrackIndex = -1;
        for (int i = 0; i < extractor.getTrackCount(); i++) {
            MediaFormat format = extractor.getTrackFormat(i);
            String mime = format.getString(MediaFormat.KEY_MIME);
            if (mime != null && mime.startsWith("audio/")) {
                audioTrackIndex = i;
                break;
            }
        }

        if (audioTrackIndex == -1) {
            extractor.release();
            return null;
        }

        extractor.selectTrack(audioTrackIndex);
        MediaFormat format = extractor.getTrackFormat(audioTrackIndex);
        String mime = format.getString(MediaFormat.KEY_MIME);
        MediaCodec codec = MediaCodec.createDecoderByType(mime);
        codec.configure(format, null, null, 0);
        codec.start();

        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        ByteBuffer[] inputBuffers = codec.getInputBuffers();
        ByteBuffer[] outputBuffers = codec.getOutputBuffers();
        MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
        boolean isEOS = false;
        long startTime = -1;

        while (true) {
            if (!isEOS) {
                int inputBufferIndex = codec.dequeueInputBuffer(TIMEOUT_US);
                if (inputBufferIndex >= 0) {
                    ByteBuffer buffer = inputBuffers[inputBufferIndex];
                    int sampleSize = extractor.readSampleData(buffer, 0);
                    if (sampleSize < 0) {
                        codec.queueInputBuffer(inputBufferIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                        isEOS = true;
                    } else {
                        long sampleTime = extractor.getSampleTime();
                        if (startTime == -1) startTime = sampleTime;
                        if (sampleTime - startTime > MAX_DURATION_US) {
                            codec.queueInputBuffer(inputBufferIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                            isEOS = true;
                        } else {
                            codec.queueInputBuffer(inputBufferIndex, 0, sampleSize, sampleTime, 0);
                            extractor.advance();
                        }
                    }
                }
            }

            int outputBufferIndex = codec.dequeueOutputBuffer(info, TIMEOUT_US);
            if (outputBufferIndex >= 0) {
                ByteBuffer buffer = outputBuffers[outputBufferIndex];
                byte[] chunk = new byte[info.size];
                buffer.get(chunk);
                buffer.clear();
                bos.write(chunk);
                codec.releaseOutputBuffer(outputBufferIndex, false);
            } else if (outputBufferIndex == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                outputBuffers = codec.getOutputBuffers();
            } else if (outputBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                // Format changed
            }

            if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                break;
            }
        }

        codec.stop();
        codec.release();
        extractor.release();

        return bos.toByteArray();
    }
}
