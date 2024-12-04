import java.io.*;

public class wumppkt {

    public static final short BUMPPROTO = 1;
    public static final short HUMPPROTO = 2;
    public static final short CHUMPPROTO = 3;

    public static short THEPROTO = BUMPPROTO;

    public static final short REQop = 1;
    public static final short DATAop = 2;
    public static final short ACKop = 3;
    public static final short ERRORop = 4;
    public static final short HANDOFFop = 5;

    public static final short SERVERPORT = 4715;
    public static final short SAMEPORT = 4716;

    public static final int INITTIMEOUT = 3000; // milliseconds
    public static final int SHORTSIZE = 2;     // in bytes
    public static final int INTSIZE = 4;
    public static final int BASESIZE = 2;
    public static final int MAXDATASIZE = 512;
    public static final int DHEADERSIZE = BASESIZE + SHORTSIZE + INTSIZE; // DATA header size
    public static final int EHEADERSIZE = BASESIZE + SHORTSIZE;
    public static final int MAXSIZE = DHEADERSIZE + MAXDATASIZE;

    public static final short EBADPORT = 1;  // packet from wrong port
    public static final short EBADPROTO = 2; // unknown protocol
    public static final short EBADOPCODE = 3; // unknown opcode
    public static final short ENOFILE = 4;   // REQ for nonexistent file
    public static final short ENOPERM = 5;   // REQ for file with wrong permissions

    static int proto(byte[] buf) {
        return buf[0];
    }

    static int opcode(byte[] buf) {
        return buf[1];
    }

    private static void w_assert(boolean cond, String s) {
        if (cond) return;
        System.err.println("Assertion failed: " + s);
        System.exit(1);
    }

    static public void setproto(short proto) {
        w_assert(proto == BUMPPROTO || proto == HUMPPROTO, "Unsupported protocol: " + proto);
        THEPROTO = proto;
    }

    // BASE class
    public static class BASE {
        private byte _proto;
        private byte _opcode;

        public BASE(int proto, int opcode) {
            _proto = (byte) proto;
            _opcode = (byte) opcode;
        }

        public BASE(byte[] buf) {
            _proto = buf[0];
            _opcode = buf[1];
        }

        public byte[] write() {
            return null;
        }

        public int size() {
            return BASESIZE;
        }

        public int proto() {
            return _proto;
        }

        public int opcode() {
            return _opcode;
        }
    }

    // REQ class
    public static class REQ extends BASE {
        private short _winsize;
        private String _filename;

        public REQ(int winsize, String filename) {
            super(THEPROTO, REQop);
            _winsize = (short) winsize;
            _filename = filename;
        }

        public byte[] write() {
            ByteArrayOutputStream baos = new ByteArrayOutputStream(size());
            DataOutputStream dos = new DataOutputStream(baos);
            try {
                dos.writeByte(proto());
                dos.writeByte(opcode());
                dos.writeShort(_winsize);
                dos.writeBytes(_filename);
                dos.writeByte(0); // null-terminate the filename
                return baos.toByteArray();
            } catch (IOException ioe) {
                System.err.println("REQ packet conversion failed");
                return null;
            }
        }

        public int size() {
            return BASESIZE + SHORTSIZE + _filename.length() + 1;
        }

        public String filename() {
            return _filename;
        }
    }

    // HANDOFF class
    public static class HANDOFF extends BASE {
        private int _newport;

        public HANDOFF(byte[] buf) {
            super(buf);
            if (buf.length < BASESIZE + SHORTSIZE) {
                throw new IllegalArgumentException("Buffer too small for HANDOFF");
            }
            ByteArrayInputStream bais = new ByteArrayInputStream(buf, BASESIZE, buf.length - BASESIZE);
            DataInputStream dis = new DataInputStream(bais);
            try {
                _newport = dis.readUnsignedShort();
            } catch (IOException ioe) {
                System.err.println("HANDOFF packet conversion failed");
                _newport = -1;
            }
        }

        public int newport() {
            return _newport;
        }
    }

    // ACK class
    public static class ACK extends BASE {
        private int _blocknum;

        public ACK(int blocknum) {
            super(THEPROTO, ACKop);
            _blocknum = blocknum;
        }

        public byte[] write() {
            ByteArrayOutputStream baos = new ByteArrayOutputStream(size());
            DataOutputStream dos = new DataOutputStream(baos);
            try {
                dos.writeByte(proto());
                dos.writeByte(opcode());
                dos.writeShort(0); // padding
                dos.writeInt(_blocknum);
                return baos.toByteArray();
            } catch (IOException ioe) {
                System.err.println("ACK packet conversion failed");
                return null;
            }
        }

        public int size() {
            return BASESIZE + SHORTSIZE + INTSIZE;
        }
    }

    // DATA class
    public static class DATA extends BASE {
        private int _blocknum;
        private byte[] _databuf;

        public DATA(byte[] buf, int buflen) {
            super(buf);
            if (buflen < DHEADERSIZE) {
                throw new IllegalArgumentException("Buffer too small for DATA");
            }
            ByteArrayInputStream bais = new ByteArrayInputStream(buf, BASESIZE, buflen - BASESIZE);
            DataInputStream dis = new DataInputStream(bais);
            try {
                dis.readShort(); // padding
                _blocknum = dis.readInt();
                _databuf = new byte[dis.available()];
                dis.read(_databuf);
            } catch (IOException ioe) {
                System.err.println("DATA packet conversion failed");
            }
        }

        public int blocknum() {
            return _blocknum;
        }

        public byte[] bytes() {
            return _databuf;
        }

        public int size() {
            return DHEADERSIZE + _databuf.length;
        }
    }

    // ERROR class
    public static class ERROR extends BASE {
        private short _errcode;

        public ERROR(short proto, short errcode) {
            super(proto, ERRORop);
            _errcode = errcode;
        }

        public ERROR(short errcode) {
            this(THEPROTO, errcode);
        }

        public ERROR(byte[] buf) {
            super(buf);
            if (buf.length < BASESIZE + SHORTSIZE) {
                throw new IllegalArgumentException("Buffer too small for ERROR");
            }
            ByteArrayInputStream bais = new ByteArrayInputStream(buf, BASESIZE, buf.length - BASESIZE);
            DataInputStream dis = new DataInputStream(bais);
            try {
                _errcode = dis.readShort();
            } catch (IOException ioe) {
                System.err.println("ERROR packet conversion failed");
                _errcode = -1;
            }
        }

        public short errcode() {
            return _errcode;
        }

        public byte[] write() {
            ByteArrayOutputStream baos = new ByteArrayOutputStream(size());
            DataOutputStream dos = new DataOutputStream(baos);
            try {
                dos.writeByte(proto());
                dos.writeByte(opcode());
                dos.writeShort(_errcode);
                return baos.toByteArray();
            } catch (IOException ioe) {
                System.err.println("ERROR packet output conversion failed");
                return null;
            }
        }

        public int size() {
            return BASESIZE + SHORTSIZE;
        }
    }
}
