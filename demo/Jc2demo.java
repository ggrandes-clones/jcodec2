package demo;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import codec2.Jcodec2;

/*---------------------------------------------------------------------------*\

  FILE........: c2demo.c
  AUTHOR......: David Rowe
  DATE CREATED: 15/11/2010

  Encodes and decodes a file of raw speech samples using Codec 2.
  Demonstrates use of Codec 2 function API.

  Note to convert a wave file to raw and vice-versa:

    $ sox file.wav -r 8000 -s -2 file.raw
    $ sox -r 8000 -s -2 file.raw file.wav

\*---------------------------------------------------------------------------*/

/*
  Copyright (C) 2010 David Rowe

  All rights reserved.

  This program is free software; you can redistribute it and/or modify
  it under the terms of the GNU Lesser General Public License version 2.1, as
  published by the Free Software Foundation.  This program is
  distributed in the hope that it will be useful, but WITHOUT ANY
  WARRANTY; without even the implied warranty of MERCHANTABILITY or
  FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public
  License for more details.

  You should have received a copy of the GNU Lesser General Public License
  along with this program; if not, see <http://www.gnu.org/licenses/>.
*/

// c2demo.c

final class Jc2demo {
	private static final String CLASS_NAME = "Jc2demo";

	@SuppressWarnings("boxing")
	public static final void main(final String[] args)
	{
		for( int i = 0; i < 10; i++ ) {
			final int r = Jcodec2.codec2_rand();
			System.out.printf("[%d] r = %d\n", i, r );
		}

		if( args.length != 3 - 1 ) {
			System.out.printf("usage: %s InputRawSpeechFile OutputRawSpeechFile\n", CLASS_NAME );
			System.exit( 1 );
			return;
		}

		FileInputStream fin = null;
		FileOutputStream fout = null;
		try {
			try {
				fin = new FileInputStream( args[1 - 1] );
			} catch(final Exception ex) {
				System.err.printf("Error opening input speech file: %s: %s.\n", args[1 - 1], ex.getMessage() );
				System.exit( 1 );
				return;
			}

			try {
				fout = new FileOutputStream( args[2 - 1] );
			} catch(final Exception ex) {
				System.err.printf("Error opening output speech file: %s: %s.\n", args[2 - 1], ex.getMessage() );
				System.exit( 1 );// FIXME 'fin' is not closed at this location
				return;
			}

/* #ifdef DUMP
			dump_on("c2demo");
#endif */

			/* Note only one set of Codec 2 states is required for an encoder
			   and decoder pair. */

			final Jcodec2 codec2 = new Jcodec2( Jcodec2.CODEC2_MODE_1300 );
			final int nsam = codec2.codec2_samples_per_frame();
			final short[] buf = new short[ nsam ];
			final int nsam2 = nsam << 1;// java
			final byte[] bytebuf = new byte[ nsam2 ];
			final int nbit = codec2.codec2_bits_per_frame();
			final byte[] bits = new byte[ nbit ];

			// while(fread(buf, sizeof(short), nsam, fin) == (size_t)nsam) {// FIXME why nsam?
			while( fin.read( bytebuf, 0, nsam2 ) == nsam2 ) {
				ByteBuffer.wrap( bytebuf, 0, nsam2 ).order( ByteOrder.LITTLE_ENDIAN ).asShortBuffer().get( buf, 0, nsam );
				codec2.codec2_encode( bits, buf );
				codec2.codec2_decode( buf, bits );
				for( int i = 0 ; i < nsam; i++ ) {
					final int data = buf[i];
					fout.write( data );
					fout.write( data >> 8 );
				}
			}
			// buf = null;
			// bytebuf = null;
			// bits = null;
			// codec2 = null;// codec2_destroy(codec2);
		} catch(final Exception ex) {
			ex.printStackTrace();
		} finally {
			if( fin != null ) {
				try { fin.close(); } catch( final IOException e ) {}
			}
			if( fout != null ) {
				try { fout.close(); } catch( final IOException e ) {}
			}
		}
		System.exit( 0 );
		return;
	}
}