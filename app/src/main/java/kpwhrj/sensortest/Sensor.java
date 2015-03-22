package kpwhrj.sensortest;
import static java.lang.Math.pow;
import java.util.List;
import java.util.UUID;
import kpwhrj.sensortest.Point3D;
import static kpwhrj.sensortest.SensorTagGatt.*;

public enum Sensor {

    IR_TEMPERATURE(UUID_IRT_SERV, UUID_IRT_DATA,UUID_IRT_CONF){

       public Point3D convert( final byte[] value){

      /*
       * The IR Temperature sensor produces two measurements; Object ( AKA target or IR) Temperature, and Ambient ( AKA die ) temperature.
       * Both need some conversion, and Object temperature is dependent on Ambient temperature.
       * They are stored as [ObjLSB, ObjMSB, AmbLSB, AmbMSB] (4 bytes) Which means we need to shift the bytes around to get the correct values.
       */

            double ambient = extractAmbientTemperature(value);
            double target = extractTargetTemperature(value, ambient);
            return new Point3D(ambient, target, 0);
        }



    private double extractAmbientTemperature(byte[] v) {
            int offset = 2;
            return shortUnsignedAtOffset(v, offset) / 128.0;
        }

        private double extractTargetTemperature(byte[] v, double ambient) {
            Integer twoByteValue = shortSignedAtOffset(v, 0);

            double Vobj2 = twoByteValue.doubleValue();
            Vobj2 *= 0.00000015625;

            double Tdie = ambient + 273.15;

            double S0 = 5.593E-14; // Calibration factor
            double a1 = 1.75E-3;
            double a2 = -1.678E-5;
            double b0 = -2.94E-5;
            double b1 = -5.7E-7;
            double b2 = 4.63E-9;
            double c2 = 13.4;
            double Tref = 298.15;
            double S = S0 * (1 + a1 * (Tdie - Tref) + a2 * pow((Tdie - Tref), 2));
            double Vos = b0 + b1 * (Tdie - Tref) + b2 * pow((Tdie - Tref), 2);
            double fObj = (Vobj2 - Vos) + c2 * pow((Vobj2 - Vos), 2);
            double tObj = pow(pow(Tdie, 4) + (fObj / S), .25);

            return tObj - 273.15;
        }


    },

    LUXOMETER(UUID_OPT_SERV, UUID_OPT_DATA, UUID_OPT_CONF) {
        @Override
        public Point3D convert(final byte [] value) {
            int mantissa;
            int exponent;
            Integer sfloat= shortUnsignedAtOffset(value, 0);

            mantissa = sfloat & 0x0FFF;
            exponent = (sfloat >> 12) & 0xFF;

            double output;
            double magnitude = pow(2.0f, exponent);
            output = (mantissa * magnitude);

            return new Point3D(output / 100.0f, 0, 0);
        }
    };

    private static Integer shortSignedAtOffset(byte[] c, int offset) {
        Integer lowerByte = (int) c[offset] & 0xFF;
        Integer upperByte = (int) c[offset + 1]; // // Interpret MSB as signed
        return (upperByte << 8) + lowerByte;
    }

    private static Integer shortUnsignedAtOffset(byte[] c, int offset) {
        Integer lowerByte = (int) c[offset] & 0xFF;
        Integer upperByte = (int) c[offset + 1] & 0xFF; // // Interpret MSB as signed
        return (upperByte << 8) + lowerByte;
    }

    private final UUID service, data, config;
    private byte enableCode; // See getEnableSensorCode for explanation.
    public static final byte DISABLE_SENSOR_CODE = 0;
    public static final byte ENABLE_SENSOR_CODE = 1;
    public static final byte CALIBRATE_SENSOR_CODE = 2;

    private Sensor(UUID service, UUID data, UUID config) {
        this.service = service;
        this.data = data;
        this.config = config;
        this.enableCode = ENABLE_SENSOR_CODE; // This is the sensor enable code for all sensors except the gyroscope
    }

    public Point3D convert(byte[] value) {
        throw new UnsupportedOperationException("Error: the individual enum classes are supposed to override this method.");
    }

    public byte getEnableSensorCode() {
        return enableCode;
    }

    public UUID getService() {
        return service;
    }

    public UUID getData() {
        return data;
    }

    public UUID getConfig() {
        return config;
    }

    public static final Sensor[] SENSOR_LIST = {IR_TEMPERATURE};
}