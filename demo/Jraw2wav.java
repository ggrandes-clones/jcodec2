package demo;

import java.io.IOException;
import java.io.RandomAccessFile;

/**
 * java helper class to get PCM wav file from a c2-raw file
 */
public final class Jraw2wav {
	private static final byte[] PCM_HEADER = {
			'R', 'I', 'F', 'F',// RIFF
			0, 0, 0, 0,// RIFF size = full size - 8
			'W', 'A', 'V', 'E', 'f', 'm', 't', ' ',// WAVEfmt
			0x10, 0, 0, 0,// Ext
			1, 0,// Format Tag
			1, 0,// number of channels
			0x40, 0x1f, 0x00, 0x00,// Samples per seconds
			(byte)0x80, 0x3e, 0x00, 0x00,// Avg Bytes per seconds
			4, 0,// BLock align
			16, 0,// Bits per sample
			'd', 'a', 't', 'a',// data
			0, 0, 0, 0// data size = full size - 44
		};
	private static final int RIFF_SIZE_OFFSET = 4;
	private static final int DATA_SIZE_OFFSET = 40;

	private static final void valueToBytes(final int value, final int byteCount, final byte[] data, int offset) {
		final int count = byteCount << 3;
		for( int i = 0; i < count; i += 8 ) {
			data[offset++] = (byte)(value >> i);
		}
	}
	private static final void setSoundFormat(final int sampleRate, final int bitsPerSample, final int channels) {
		valueToBytes( channels, 2, PCM_HEADER, 22 );// Channels
		valueToBytes( sampleRate, 4, PCM_HEADER, 24 );// Sample rate
		valueToBytes( sampleRate * channels * (bitsPerSample >> 3), 4, PCM_HEADER, 28 );// Avg Bytes per seconds
		valueToBytes( (bitsPerSample >> 3) * channels, 2, PCM_HEADER, 32 );// BLock align
		valueToBytes( bitsPerSample, 2, PCM_HEADER, 34 );// Bits per sample
	}
	private static final void setDataLength(final int bytesWritten) {
		int size = bytesWritten;
		valueToBytes( size, 4, PCM_HEADER, DATA_SIZE_OFFSET );
		size += DATA_SIZE_OFFSET - RIFF_SIZE_OFFSET;
		valueToBytes( size, 4, PCM_HEADER, RIFF_SIZE_OFFSET );
	}

	public static final void main(final String[] args) {
		if( args.length != 2 ) {
			System.out.println("usage: Jraw2wav InputC2File OutputWavFile");
			return;
		}
		RandomAccessFile out = null;
		RandomAccessFile in = null;
		try {
			in = new RandomAccessFile( args[0], "r" );
			out = new RandomAccessFile( args[1], "rw" );
			//
			setSoundFormat( 8000, 16, 1 );
			setDataLength( (int)in.length() );
			//
			out.write( PCM_HEADER );
			//
			final byte[] b = new byte[4096];
			int r;
			while( (r = in.read( b )) > 0 ) {
				out.write( b, 0, r );
			}
		} catch(final Exception e) {
			e.printStackTrace();
		} finally {
			if( in != null ) {
				try { in.close(); } catch( final IOException e ) { }
			}
			if( out != null ) {
				try { out.close(); } catch( final IOException e ) { }
			}
		}
	}
}
