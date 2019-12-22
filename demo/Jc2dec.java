package demo;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Random;

import codec2.Jcodec2;
import gnu.getopt.Getopt;
import gnu.getopt.LongOpt;

/*---------------------------------------------------------------------------*\

  FILE........: c2dec.c
  AUTHOR......: David Rowe
  DATE CREATED: 23/8/2010

  Decodes a file of bits to a file of raw speech samples using codec2.

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

// c2dec.c

final class Jc2dec {
	private static final String CLASS_NAME = "Jc2dec";
	// private static final boolean DUMP = false;

	private static final int NONE          = 0;  /* no bit errors                          */
	private static final int UNIFORM       = 1;  /* random bit errors                      */
	private static final int TWO_STATE     = 2;  /* Two state error model                  */
	private static final int UNIFORM_RANGE = 3;  /* random bit errors over a certain range */

	@SuppressWarnings("boxing")
	public static final void main(final String[] args)
	{
		InputStream fin = null;
		OutputStream fout = null;
		InputStream fber = null;// FIXME fber never closes

		try {
			final String opt_string = "h:";
			final LongOpt long_options[] = {
					new LongOpt( "ber", LongOpt.REQUIRED_ARGUMENT, null, 0 ),
					new LongOpt( "startbit", LongOpt.REQUIRED_ARGUMENT, null, 0 ),
					new LongOpt( "endbit", LongOpt.REQUIRED_ARGUMENT, null, 0 ),
					new LongOpt( "berfile", LongOpt.REQUIRED_ARGUMENT, null, 0 ),
					new LongOpt( "natural", LongOpt.NO_ARGUMENT, null, 0 ),
					new LongOpt( "softdec", LongOpt.NO_ARGUMENT, null, 0 ),
/* #ifdef DUMP
					new LongOpt( "dump", LongOpt.REQUIRED_ARGUMENT, &dump, 1 },
#endif */
					new LongOpt( "help", LongOpt.NO_ARGUMENT, null, 'h' ),
					// { NULL, no_argument, NULL, 0 }
				};
			final int num_opts = long_options.length;

			if( args.length < 4 - 1 ) {
				print_help( long_options, num_opts, args );
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
			final int bit_rate = Integer.valueOf( args[1 - 1] );

			if( args[2 - 1].compareTo("-") == 0 ) {
				fin = System.in;
			} else {
				try {
					fin = new FileInputStream( args[2 - 1] );
				} catch(final Exception e) {
					System.err.printf("Error opening input bit file: %s: %s.\n", args[2 - 1], e.getMessage() );
					System.exit( 1 );
					return;
				}
			}

			if( args[3 - 1].compareTo("-") == 0 ) {
				fout = System.out;
			} else {
				try {
					fout = new FileOutputStream( args[3 - 1] );
				} catch(final Exception e) {
					System.err.printf("Error opening output speech file: %s: %s.\n", args[3 - 1], e.getMessage() );
					System.exit( 1 );
					return;
				}
			}

			int error_mode = NONE;
			float ber = 0.0f;
			final float burst_length = 0.0f, burst_period = 0.0f;
			float burst_timer = 0.0f;
			boolean natural = false, softdec = false;

			final Jcodec2 codec2 = new Jcodec2( mode );
			final int nsam = codec2.codec2_samples_per_frame();
			final int nbit = codec2.codec2_bits_per_frame();
			final short[] buf = new short[ nsam ];
			final int nbyte = (nbit + 7) >> 3;
			final byte[] bits = new byte[ nbyte ];
			final float[] softdec_bits = new float[ nbit ];
			int bit_errors = 0, bits_proc = 0;
			int nstart_bit = 0;
			int nend_bit = nbit - 1;

			final Getopt g = new Getopt( CLASS_NAME, args, opt_string, long_options );
			g.setOpterr( false );
			while( true ) {
				final int opt = g.getopt();// getopt_long(argc, argv, opt_string, long_options, &option_index);
				if( opt == -1 ) {
					break;
				}
				switch( opt ) {
				case 0:
					final String name = long_options[ g.getLongind() ].getName();// java
					final String optarg = g.getOptarg();
					if( name.compareTo("ber") == 0 ) {
						ber = Float.parseFloat( optarg );
						error_mode = UNIFORM;
					} else if( name.compareTo("startbit") == 0 ) {
						nstart_bit = Integer.parseInt( optarg );
					} else if( name.compareTo("endbit") == 0 ) {
						nend_bit = Integer.parseInt( optarg );
					} else if( name.compareTo("berfile") == 0 ) {
						try {
							fber = new FileInputStream( optarg );
						} catch(final Exception e) {
							System.err.printf("Error opening BER file: %s %s.\n", optarg, e.getMessage() );
							System.exit( 1 );
							return;
						}

					} else if( name.compareTo("natural") == 0 ) {
						natural = true;
					} else if( name.compareTo("softdec") == 0 ) {
						softdec = true;
					}
/* #ifdef DUMP
					else if( name.compareTo("dump") == 0 ) {
						if( dump ) {
							dump_on( optarg );
						}
					}
#endif */
					break;

				case 'h':
					print_help( long_options, num_opts, args );
					break;

				default:
					/* This will never be reached */
					break;
				}
			}
			// assert( nend_bit <= nbit );
			codec2.codec2_set_natural_or_gray( ! natural );
			//printf("%d %d\n", nstart_bit, nend_bit);

			//fprintf(stderr, "softdec: %d natural: %d\n", softdec, natural);
			final int nbit4 = nbit << 2;// java
			final byte[] bytebuf = new byte[ nbit4 ];// java helper
			boolean ret;
			if( softdec ) {
				ret = (fin.read( bytebuf, 0, nbit4 ) == nbit4);// FIXME why nbit?
				ByteBuffer.wrap( bytebuf, 0, nbit4 ).order( ByteOrder.LITTLE_ENDIAN ).asFloatBuffer().get( softdec_bits, 0, nbit );
			} else {
				ret = (fin.read( bits, 0, nbyte ) == nbyte);
			}

			int state = 0;// java: to fix The local variable state may not have been initialized
			final Random rand = new Random();
			while( ret ) {
				// apply bit errors, MSB of byte 0 is bit 0 in frame, only works in packed mode

				if( (error_mode == UNIFORM) || (error_mode == UNIFORM_RANGE) ) {
					// assert( softdec == 0 );
					for( int i = nstart_bit; i < nend_bit + 1; i++ ) {
						final float r = (float)rand.nextInt( 0x8000 ) / 0x7fff;
						if( r < ber ) {
							final int bytes = i >> 3;
							//printf("nbyte %d nbit %d i %d byte %d bits[%d] 0x%0x ", nbyte, nbit, i, byte, byte, bits[byte]);
							final int mask = 1 << (7 - i + (bytes << 3));
							bits[bytes] ^= mask;
							//printf("shift: %d mask: 0x%0x bits[%d] 0x%0x\n", 7 - i + byte*8, mask, byte, bits[byte] );
							bit_errors++;
						}
						bits_proc++;
					}
				}

				if( error_mode == TWO_STATE ) {
					// assert( softdec == 0 );
					burst_timer += (float)nbit / bit_rate;
					System.err.printf("burst_timer: %f  state: %d\n", burst_timer, state );

					int next_state = state;
					switch( state ) {
					case 0:

						/* clear channel state - no bit errors */

						if( burst_timer > (burst_period - burst_length) ) {
							next_state = 1;
						}
						break;

					case 1:

						/* burst error state - 50% bit error rate */

						for( int i = nstart_bit; i < nend_bit + 1; i++ ) {
							final float r = (float)rand.nextInt( 0x8000 ) / 0x7fff;
							if( r < 0.5f ) {
								final int bytes = i >> 3;
								bits[ bytes ] ^= 1 << (7 - i + (bytes << 3));
								bit_errors++;
							}
							bits_proc++;
						}

						if( burst_timer > burst_period ) {
							burst_timer = 0.0f;
							next_state = 0;
						}
						break;

					}

					state = next_state;
				}

				float ber_est;
				if( fber != null ) {
					if( fber.read( bytebuf, 0, 4 ) != 4 ) {
						System.err.printf("ran out of BER estimates!\n");
						System.exit( 1 );
						return;
					}
					int i = bytebuf[0] & 0xff;
					i |= (bytebuf[1] & 0xff) << 8;
					i |= (bytebuf[2] & 0xff) << 16;
					i |= (bytebuf[3] & 0xff) << 24;
					ber_est = Float.intBitsToFloat( i );
					//fprintf(stderr, "ber_est: %f\n", ber_est);
				} else {
					ber_est = 0.0f;
				}

				if( softdec ) {
					/* pack bits, MSB received first  */

					int bit = 7, bytes = 0;
					for( int i = 0; i < nbyte; i++ ) {
						bits[i] = 0;
					}
					for( int i = 0; i < nbit; i++ ) {
						bits[bytes] |= softdec_bits[i] < 0.0f ? 1 << bit: 0;
						bit--;
						if( bit < 0 ) {
							bit = 7;
							bytes++;
						}
					}
					// java: softdec_bits never uses inside in the production code
					// Jcodec2.codec2_set_softdec( codec2, softdec_bits );
				}

				codec2.codec2_decode_ber( buf, bits, ber_est );

				for( int i = 0; i < nsam; i++ ) {
					final int data = buf[i];
					fout.write( data );
					fout.write( data >> 8 );
				}

				//if this is in a pipeline, we probably don't want the usual
				//buffering to occur

				if( fout == System.out ) {
					System.out.flush();
				}
				if( fin == System.in ) {
					while( (System.in.read()) >= 0 ) {
						;
					}
				}

				if( softdec ) {
					ret = (fin.read( bytebuf, 0, nbit4 ) == nbit4);// FIXME why nbit?
					ByteBuffer.wrap( bytebuf, 0, nbit4 ).order( ByteOrder.LITTLE_ENDIAN ).asFloatBuffer().get( softdec_bits, 0, nbit );
				} else {
					ret = (fin.read( bits, 0, nbyte ) == nbyte );
				}
			}

			if( error_mode != 0 ) {
				System.err.printf("actual BER: %1.3f\n", (float)bit_errors / bits_proc);
			}

			// codec2 = null;// codec2_destroy( codec2 );

			// buf = null;
			// bytebuf = null;
			// bits = null;
			// softdec_bits = null;
		} catch(final Exception ex) {
			ex.printStackTrace();
		} finally {
			if( fber != null ) {
				try { fber.close(); } catch( final IOException e ) {}
			}
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

	private static final void print_help(final LongOpt[] long_options, final int num_opts, final String args[])
	{
		System.err.printf("\nJc2dec - Codec 2 decoder and bit error simulation program\n" +
				"usage: %s 3200|2400|1400}1300|1200 InputFile OutputRawFile [OPTIONS]\n\n" +
				"Options:\n", CLASS_NAME );
		for( int i = 0; i < num_opts - 1; i++ ) {
			final String name = long_options[i].getName();// java
			String option_parameters;
			if( long_options[i].getHasArg() == LongOpt.NO_ARGUMENT ) {
				option_parameters = "";
			} else if( "ber".compareTo( name ) == 0 ) {
				option_parameters = " BER";
			} else if( "startbit".compareTo( name ) == 0 ) {
				option_parameters = " startBit";
			} else if( "endbit".compareTo( name ) == 0 ) {
				option_parameters = " endBit";
			} else if( "berfile".compareTo( name ) == 0 ) {
				option_parameters = " berFileName";
			} else if( "dump".compareTo( name ) == 0 ) {
				option_parameters = " dumpFilePrefix";
			} else {
				option_parameters = " <UNDOCUMENTED parameter>";
			}
			System.err.printf("\t--%s%s\n", long_options[i].getName(), option_parameters );
		}
		System.exit( 1 );
		return;
	}
}