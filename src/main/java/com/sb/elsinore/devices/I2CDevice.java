package com.sb.elsinore.devices;

import com.sb.elsinore.BrewServer;
import com.sb.elsinore.LaunchControl;
import com.sun.jna.*;
import com.sun.jna.ptr.IntByReference;
import org.w3c.dom.Element;

import java.util.Arrays;
import java.util.List;

/**
 * Wrap an I2C device to make it easy to read/write using standard paths and formats
 * Created by doug on 11/07/15.
 */
public abstract class I2CDevice {
    public static String DEV_NUMBER = "Number";
    public static String DEV_ADDRESS = "Address";
    public static String DEV_CHANNEL = "Channel";
    public static String DEV_TYPE = "Type";
    public static String I2C_NODE = "I2C";
    public static String DEV_NAME = "BASE";

    public static int I2C_BUFSIZE = 64;
    public static String BASE_PATH = "/dev/i2c-%s";
    public static int O_RDWR = 0x02;
    /* /dev/i2c-X ioctl commands.  The ioctl's parameter is always an
 * unsigned long, except for:
 *  - I2C_FUNCS, takes pointer to an unsigned long
 *  - I2C_RDWR, takes pointer to struct i2c_rdwr_ioctl_data
 *  - I2C_SMBUS, takes pointer to struct i2c_smbus_ioctl_data
 */
    public static int I2C_RETRIES = 0x0701;  /* number of times a device address should
                   be polled when not acknowledging */
    public static int I2C_TIMEOUT = 0x0702;  /* set timeout in units of 10 ms */

    /* NOTE: Slave address is 7 or 10 bits, but 10-bit addresses
     * are NOT supported! (due to code brokenness)
     */
    public static int I2C_SLAVE  = 0x0703;  /* Use this slave address */
    public static int I2C_SLAVE_FORCE = 0x0706;  /* Use this slave address, even if it
                   is already in use by a driver! */
    public static int I2C_TENBIT = 0x0704;  /* 0 for 7 bit addrs, != 0 for 10 bit */

    public static int I2C_FUNCS = 0x0705;  /* Get the adapter functionality mask */

    public static int I2C_RDWR  = 0x0707;  /* Combined R/W transfer (one STOP only) */

    public static int I2C_PEC   = 0x0708;  /* != 0 to use PEC with SMBus */
    public static int I2C_SMBUS  = 0x0720;  /* SMBus transfer */

    protected int device_number = -1;
    protected int address = 0x00;

    protected Linux_C_lib libC = new Linux_C_lib_DirectMapping();
    private String devNumberString;
    private static String[] availableTypes;

    public static I2CDevice create(String devNumber, String devAddress, String devType) {
        I2CDevice i2cDevice = null;
        int iDevAddress = 0;
        if (devAddress.startsWith("0x"))
        {
            iDevAddress = Integer.valueOf(devAddress.substring(2), 16);
        }
        else
        {
            iDevAddress = Integer.valueOf(devAddress);
        }

        if (devType.equals(ADS1015.DEV_NAME))
        {
            i2cDevice = new ADS1015(Integer.parseInt(devNumber), iDevAddress);
        }
        else if (devType.equals(ADS1115.DEV_NAME))
        {
            i2cDevice = new ADS1115(Integer.parseInt(devNumber), iDevAddress);
        }
        return i2cDevice;
    }

    public static String[] getAvailableTypes() {
        String[] types = new String[2];
        types[0] = ADS1015.DEV_NAME;
        types[1] = ADS1115.DEV_NAME;
        return types;
    }

    public abstract String getDevName();

    public String getDevNumberString() {
        if (device_number != -1)
        {
            return Integer.toString(device_number);
        }
        return "";
    }

    public interface Linux_C_lib extends com.sun.jna.Library {

        public long memcpy(int[] dst, short[] src, long n);

        public int memcpy(int[] dst, short[] src, int n);

        public int pipe(int[] fds);

        public int tcdrain(int fd);

        public int fcntl(int fd, int cmd, int arg);

        public int ioctl(int fd, int cmd, int arg);

        public int open(String path, int flags);

        public int close(int fd);

        public int write(int fd, byte[] buffer, int count);

        public int read(int fd, byte[] buffer, int count);

        public long write(int fd, byte[] buffer, long count);

        public long read(int fd, byte[] buffer, long count);

        public int select(int n, int[] read, int[] write, int[] error, timeval timeout);

        public int poll(int[] fds, int nfds, int timeout);

        public int tcflush(int fd, int qs);

        public void perror(String msg);

        public int tcsendbreak(int fd, int duration);

        static public class timeval extends Structure {
            public NativeLong tv_sec;
            public NativeLong tv_usec;

            @Override
            protected List getFieldOrder() {
                return Arrays.asList(//
                        "tv_sec",//
                        "tv_usec"//
                );
            }
        }


    }

    public static class Linux_C_lib_DirectMapping implements Linux_C_lib {

        native public long memcpy(int[] dst, short[] src, long n);

        native public int memcpy(int[] dst, short[] src, int n);

        native public int pipe(int[] fds);

        native public int tcdrain(int fd);

        native public int fcntl(int fd, int cmd, int arg);

        native public int ioctl(int fd, int cmd, int arg);

        native public int open(String path, int flags);

        native public int close(int fd);

        native public int write(int fd, byte[] buffer, int count);

        native public int read(int fd, byte[] buffer, int count);

        native public long write(int fd, byte[] buffer, long count);

        native public long read(int fd, byte[] buffer, long count);

        native public int select(int n, int[] read, int[] write, int[] error, timeval timeout);

        native public int poll(int[] fds, int nfds, int timeout);

        native public int tcflush(int fd, int qs);

        native public void perror(String msg);

        native public int tcsendbreak(int fd, int duration);

        static {
            try {
                Native.register("c");
                BrewServer.LOG.warning("OK: Managed to load CLibrary");
            }
            catch (Exception e) {
                e.printStackTrace();
                BrewServer.LOG.warning("BAD: Failed to load CLibrary");
            }
        }
    }

    public I2CDevice(int deviceNo, int address)
    {
        this.device_number = deviceNo;
        this.address = address;
    }

    protected boolean _adsInitialised = false;
    protected int fd = -1;

    public ads1015error error = ads1015error.ADS1015_ERROR_OK;

    enum ads1015error
    {
        ADS1015_ERROR_OK,                // Everything executed normally
        ADS1015_ERROR_I2CINIT,               // Unable to initialise I2C
        ADS1015_ERROR_I2CBUSY,               // I2C already in use
        ADS1015_ERROR_INVALIDCHANNEL,        // Invalid channel specified
        ADS1015_ERROR_LAST
    }

    public byte[] I2CMasterBuffer = new byte[I2C_BUFSIZE];
    public byte[] I2CSlaveBuffer = new byte[I2C_BUFSIZE];
    public int I2CReadLength, I2CWriteLength;

    public ads1015error ads1015WriteRegister (int reg, int value)
    {
        ads1015error error = ads1015error.ADS1015_ERROR_OK;

        // Clear write buffers
        int i;
        for ( i = 0; i < I2C_BUFSIZE; i++ )
        {
            I2CMasterBuffer[i] = 0x00;
        }

        I2CWriteLength = 3;
        I2CReadLength = 0;
        I2CMasterBuffer[0] = (byte) reg;                   // Register
        I2CMasterBuffer[1] = (byte) 0xf3;//(value >> 8);            // Upper 8-bits
        I2CMasterBuffer[2] = (byte) 0x03;//(value & 0xFF);          // Lower 8-bits

        int ret = libC.write(fd, I2CMasterBuffer, I2CWriteLength);
        if (ret != I2CWriteLength)
        {
            BrewServer.LOG.warning(String.format("Failed to write to I2C Device %s. Error %s", ret, Native.getLastError()));
        }
        // ToDo: Add in proper I2C error-checking
        return error;
    }

    public ads1015error ads1015Init()
    {
        ads1015error error = ads1015error.ADS1015_ERROR_OK;

        // Initialise I2C
        if (!i2cInit())
        {
            return ads1015error.ADS1015_ERROR_I2CINIT;    /* Fatal error */
        }

        _adsInitialised = true;
        return error;

    }

    public int ina219Read16(int reg)
    {

        // Clear write buffers
        for (int i = 0; i < I2C_BUFSIZE; i++ )
        {
            I2CMasterBuffer[i] = 0x00;
        }

        I2CWriteLength = 2;
        I2CReadLength = 2;
        I2CSlaveBuffer[0] = (byte) 0x0;           // I2C device address
        I2CSlaveBuffer[1] = (byte) 0x0;                       // Command register

        while ((I2CSlaveBuffer[0] & 0x80) == 0)
        {
            libC.read(fd, I2CSlaveBuffer, I2CReadLength);
        }
        I2CMasterBuffer[0] = 0x0;
        libC.write(fd, I2CMasterBuffer, 1);

        if (I2CReadLength != libC.read(fd, I2CMasterBuffer, I2CReadLength))
        {
            BrewServer.LOG.warning(String.format("Error reading %s", Native.getLastError()));
        }

        // Shift values to create properly formed integer
        return ((I2CMasterBuffer[0] << 8) | I2CMasterBuffer[1]);
    }

    public boolean i2cInit()
    {
        String devpath = String.format(I2CDevice.BASE_PATH, device_number);
        fd = libC.open(devpath, O_RDWR);
        if (fd < 0)
        {
            BrewServer.LOG.warning(String.format("Failed to read from %s. Error %s", devpath, Native.getLastError()));
            return false;
        }
        else {
            BrewServer.LOG.warning(String.format("Opened fd %s", fd));
        }

        int iovalue = libC.ioctl(fd, I2CDevice.I2C_SLAVE, address);

        if (iovalue < 0)
        {
            BrewServer.LOG.warning(String.format("Failed to open slave device at address %s, wrote %s", address, iovalue));
            BrewServer.LOG.warning(String.format("Error %s", Native.getLastError()));
            return false;
        }
        else
        {
            BrewServer.LOG.warning(String.format("I2C Slave %s opened %s", address, iovalue));
        }
        return true;
    }

    public abstract float readValue(int devChannel);

    public static I2CDevice parseElement(Element i2cElement) {
        String devType = LaunchControl.getFirstElement(i2cElement, DEV_ADDRESS).getNodeValue();
        if (devType == null) {
            return null;
        }

        String devNumber = LaunchControl.getFirstElement(i2cElement, DEV_NUMBER).getNodeValue();
        String devAddress = LaunchControl.getFirstElement(i2cElement, DEV_ADDRESS).getNodeValue();

        return create(devNumber, devAddress, devType);
    }

    public int getAddress()
    {
        return this.address;
    }

    public int getDevNumber()
    {
        return this.device_number;
    }
}