package demo;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

public final class Jwav2raw {
	private static final int PCM_HEADER_SIZE = 44;

	public static final void main(final String[] args) {
		if( args.length != 2 ) {
			System.out.println("usage: Jwav2raw InputWavFile OutputRawFile");
			return;
		}
		FileOutputStream out = null;
		FileInputStream in = null;
		try {
			in = new FileInputStream( args[0] );
			out = new FileOutputStream( args[1] );
			//
			final byte[] b = new byte[4096];
			int r = 0;
			while( (r += in.read( b, 0, 1 )) < PCM_HEADER_SIZE ) {
			}
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
