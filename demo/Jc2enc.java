package demo;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import codec2.Jcodec2;

/*---------------------------------------------------------------------------*\

  FILE........: c2enc.c
  AUTHOR......: David Rowe
  DATE CREATED: 23/8/2010

  Encodes a file of raw speech samples using codec2 and outputs a file
  of bits.

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

// c2enc.c

final class Jc2enc {
	// private static final String CLASS_NAME = "Jc2enc";

	public static final void main(final String[] args)
	{
		if( args.length < 4 - 1 ) {// java -1, no path
			System.out.print("usage: Jc2enc 3200|2400|1600|1400|1300|1200|700|700B InputRawspeechFile OutputBitFile [--natural] [--softdec]\n");
			System.out.print("e.g    Jc2enc 1400 ../raw/hts1a.raw hts1a.c2\n");
			System.out.print("e.g    Jc2enc 1300 ../raw/hts1a.raw hts1a.c2 --natural\n");
			System.exit( 1 );
			return;
		}

		int mode;
		if( args[1 - 1].compareTo("3200") == 0 ) {
			mode = Jcodec2.CODEC2_MODE_3200;
		} else if( args[1 - 1].compareTo("2400") == 0 ) {
			mode = Jcodec2.CODEC2_MODE_2400;
		} else if( args[1 - 1].compareTo("1600") == 0 ) {
			mode = Jcodec2.CODEC2_MODE_1600;
		} else if( args[1 - 1].compareTo("1400") == 0 ) {
			mode = Jcodec2.CODEC2_MODE_1400;
		} else if( args[1 - 1].compareTo("1300") == 0 ) {
			mode = Jcodec2.CODEC2_MODE_1300;
		} else if( args[1 - 1].compareTo("1200") == 0 ) {
			mode = Jcodec2.CODEC2_MODE_1200;
		} else if( args[1 - 1].compareTo("700") == 0 ) {
			mode = Jcodec2.CODEC2_MODE_700;
		} else if( args[1 - 1].compareTo("700B") == 0 ) {
			mode = Jcodec2.CODEC2_MODE_700B;
		} else {
			System.err.printf("Error in mode: %s.  Must be 3200, 2400, 1600, 1400, 1300, 1200, 700 or 700B\n", args[1 - 1]);
			System.exit( 1 );
			return;
		}

		InputStream   fin = null;
		OutputStream  fout = null;

		if( args[2 - 1].compareTo("-") == 0 ) {
			fin = System.in;
		} else {
			try {
				fin = new FileInputStream( args[2 - 1] );
			} catch(final Exception ex) {
				System.err.printf("Error opening input speech file: %s: %s.\n", args[2 - 1], ex.getMessage() );
				System.exit( 1 );
				return;
			}
		}

		if( args[3 - 1].compareTo("-") == 0 ) {
			fout = System.out;
		} else {
			try {
				fout = new FileOutputStream( args[3 - 1] );
			} catch(final Exception ex) {
				System.err.printf("Error opening output compressed bit file: %s: %s.\n", args[3 - 1], ex.getMessage() );
				System.exit( 1 );
				return;
			}
		}

		final Jcodec2 codec2 = new Jcodec2( mode );
		final int nsam = codec2.codec2_samples_per_frame();
		final int nbit = codec2.codec2_bits_per_frame();
		final short[] buf = new short[ nsam ];
		final int nsam2 = nsam << 1;// java
		final byte[] bytebuf = new byte[ nsam2];// java helper
		final int nbyte = (nbit + 7) >> 3;

		final byte[] bits = new byte[ nbyte ];
		final float[] unpacked_bits = new float[ nbit ];

		boolean gray = true;
		boolean softdec = false;
		for( int i = 4 - 1; i < args.length; i++ ) {
			if( args[i].compareTo("--natural") == 0 ) {
				gray = false;
			}
			if( args[i].compareTo("--softdec") == 0 ) {
				softdec = true;
			}
		}
		codec2.codec2_set_natural_or_gray( gray );
		//fprintf(stderr,"gray: %d softdec: %d\n", gray, softdec);

		try {
			// while( fread(buf, sizeof(short), nsam, fin) == (size_t)nsam ) {// FIXME why nsam?
			while( fin.read( bytebuf, 0, nsam2 ) == nsam2 ) {
				ByteBuffer.wrap( bytebuf, 0, nsam2 ).order( ByteOrder.LITTLE_ENDIAN ).asShortBuffer().get( buf, 0, nsam );

				codec2.codec2_encode( bits, buf );

				if( softdec ) {
					/* unpack bits, MSB first, send as soft decision float */

					int bit = 7;
					int bytes = 0;
					for( int i = 0; i < nbit; i++ ) {
						unpacked_bits[i] = 1.0f - (float)(((bits[bytes] >> bit) & 0x1) << 1);
						bit--;
						if( bit < 0 ) {
							bit = 7;
							bytes++;
						}
					}
					for( int i = 0; i < nbit; i++ ) {
						final int data = Float.floatToRawIntBits( unpacked_bits[i] );
						fout.write( data );
						fout.write( data >> 8 );
						fout.write( data >> 16 );
						fout.write( data >> 24 );
					}
				} else {
					fout.write( bits, 0, nbyte );
				}

				// if this is in a pipeline, we probably don't want the usual
				// buffering to occur

				if( fout == System.out ) {
					System.out.flush();
				}
				if( fin == System.in ) {
					while( (System.in.read()) >= 0 ) {
						;
					}
				}
			}
		} catch(final Exception ex) {
			ex.printStackTrace();
		} finally {

			// codec2 = null;// codec2_destroy( codec2 );

			// buf = null;
			// bytebuf = null;
			// bits = null;
			// unpacked_bits = null;
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