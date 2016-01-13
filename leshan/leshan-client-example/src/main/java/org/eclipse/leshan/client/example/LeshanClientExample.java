/*******************************************************************************
 * Copyright (c) 2013-2015 Sierra Wireless and others.
 * 
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * and Eclipse Distribution License v1.0 which accompany this distribution.
 * 
 * The Eclipse Public License is available at
 *    http://www.eclipse.org/legal/epl-v10.html
 * and the Eclipse Distribution License is available at
 *    http://www.eclipse.org/org/documents/edl-v10.html.
 * 
 * Contributors:
 *     Zebra Technologies - initial API and implementation
 *     Sierra Wireless, - initial API and implementation
 *     Bosch Software Innovations GmbH, - initial API and implementation
 *******************************************************************************/

package org.eclipse.leshan.client.example;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.TimeZone;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.TimeUnit;

import org.eclipse.leshan.ResponseCode;
import org.eclipse.leshan.client.californium.LeshanClient;
import org.eclipse.leshan.client.resource.BaseInstanceEnabler;
import org.eclipse.leshan.client.resource.ObjectEnabler;
import org.eclipse.leshan.client.resource.ObjectsInitializer;
import org.eclipse.leshan.core.model.LwM2mModel;
import org.eclipse.leshan.core.model.ObjectLoader;
import org.eclipse.leshan.core.model.ResourceModel.Type;
import org.eclipse.leshan.core.node.LwM2mResource;
import org.eclipse.leshan.core.request.DeregisterRequest;
import org.eclipse.leshan.core.request.RegisterRequest;
import org.eclipse.leshan.core.response.ExecuteResponse;
import org.eclipse.leshan.core.response.ReadResponse;
import org.eclipse.leshan.core.response.RegisterResponse;
import org.eclipse.leshan.core.response.WriteResponse;

/*
 * To build: 
 * mvn assembly:assembly -DdescriptorId=jar-with-dependencies
 * To use:
 * java -jar target/leshan-client-*-SNAPSHOT-jar-with-dependencies.jar 127.0.0.1 5683
 */
public class LeshanClientExample {
    private String registrationID;
    private final Location locationInstance = new Location();
    private final Display displayInstance = new Display();
    private final Joystick joystickInstance = new Joystick();
    private final ParkingSpot parkingSpotInstance;

    public static void main(final String[] args) {
        if (args.length != 5 && args.length != 3) {
            System.out.println(
                    "Usage:\njava -jar target/leshan-client-example-*-SNAPSHOT-jar-with-dependencies.jar [ClientIP] [ClientPort] ServerIP ServerPort groupnr");
        } else {
            if (args.length == 4)
                new LeshanClientExample(args[0], Integer.parseInt(args[1]), args[2], Integer.parseInt(args[3]),
                        Integer.parseInt(args[4]));
            else
                new LeshanClientExample("0", 0, args[0], Integer.parseInt(args[1]), Integer.parseInt(args[2]));
        }
    }

    public LeshanClientExample(final String localHostName, final int localPort, final String serverHostName,
            final int serverPort, final int groupNr) {

        parkingSpotInstance = new ParkingSpot(groupNr);
        // Initialize object list
        File file = new File("./assignment-objects-spec.json");

        System.out.println(file.getAbsolutePath());

        LwM2mModel model = null;
        try {
            model = new LwM2mModel(
                    ObjectLoader.loadJsonStream(new FileInputStream(new File("./assignment-objects-spec.json"))));
        } catch (FileNotFoundException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        System.out.println(model.getObjectModels());
        ObjectsInitializer initializer = new ObjectsInitializer(model);

        initializer.setClassForObject(3, Device.class);
        initializer.setClassForObject(3341, Display.class);
        initializer.setClassForObject(3345, Joystick.class);
        initializer.setClassForObject(32700, ParkingSpot.class);

        initializer.setInstancesForObject(6, locationInstance);
        initializer.setInstancesForObject(3341, displayInstance);
        initializer.setInstancesForObject(3345, joystickInstance);
        initializer.setInstancesForObject(32700, parkingSpotInstance);
        List<ObjectEnabler> enablers = initializer.createMandatory();
        enablers.add(initializer.create(6));
        enablers.add(initializer.create(3341));
        enablers.add(initializer.create(3345));
        enablers.add(initializer.create(32700));

        // Create client
        final InetSocketAddress clientAddress = new InetSocketAddress(localHostName, localPort);
        final InetSocketAddress serverAddress = new InetSocketAddress(serverHostName, serverPort);

        final LeshanClient client = new LeshanClient(clientAddress, serverAddress, enablers);

        // Start the client
        client.start();
        // Register to the server
        final String endpointIdentifier = "Parking-Spot-" + groupNr;
        RegisterResponse response = client.send(new RegisterRequest(endpointIdentifier));
        if (response == null) {
            System.out.println("Registration request timeout");
            return;
        }

        System.out.println("Device Registration (Success? " + response.getCode() + ")");
        if (response.getCode() != ResponseCode.CREATED) {
            // TODO Should we have a error message on response ?
            // System.err.println("\tDevice Registration Error: " + response.getErrorMessage());
            System.err.println(
                    "If you're having issues connecting to the LWM2M endpoint, try using the DTLS port instead");
            return;
        }

        registrationID = response.getRegistrationID();
        System.out.println("\tDevice: Registered Client Location '" + registrationID + "'");

        // Deregister on shutdown and stop client.
        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run() {
                if (registrationID != null) {
                    System.out.println("\tDevice: Deregistering Client '" + registrationID + "'");
                    client.send(new DeregisterRequest(registrationID), 1000);
                    client.stop();
                }
            }
        });

        // Change the location through the Console
        while (true) {
            try {
                Process p = Runtime.getRuntime().exec("python /home/pi/joystick.py");
                p.waitFor(100, TimeUnit.MILLISECONDS);
                BufferedReader stdInput = new BufferedReader(new InputStreamReader(p.getInputStream()));

                BufferedReader stdError = new BufferedReader(new InputStreamReader(p.getErrorStream()));
                String s = null;
                while ((s = stdInput.readLine()) != null) {
                    joystickInstance.sety(Integer.parseInt(s));
                }

            } catch (IOException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            } catch (InterruptedException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }

    }

    public static class Device extends BaseInstanceEnabler {

        public Device() {
            // notify new date each 5 second
            Timer timer = new Timer();
            timer.schedule(new TimerTask() {
                @Override
                public void run() {
                    fireResourcesChange(13);
                }
            }, 5000, 5000);
        }

        @Override
        public ReadResponse read(int resourceid) {
            System.out.println("Read on Device Resource " + resourceid);
            switch (resourceid) {
            case 0:
                return ReadResponse.success(resourceid, getManufacturer());
            case 1:
                return ReadResponse.success(resourceid, getModelNumber());
            case 2:
                return ReadResponse.success(resourceid, getSerialNumber());
            case 3:
                return ReadResponse.success(resourceid, getFirmwareVersion());
            case 9:
                return ReadResponse.success(resourceid, getBatteryLevel());
            case 10:
                return ReadResponse.success(resourceid, getMemoryFree());
            case 11:
                Map<Integer, Long> errorCodes = new HashMap<>();
                errorCodes.put(0, getErrorCode());
                return ReadResponse.success(resourceid, errorCodes, Type.INTEGER);
            case 13:
                return ReadResponse.success(resourceid, getCurrentTime());
            case 14:
                return ReadResponse.success(resourceid, getUtcOffset());
            case 15:
                return ReadResponse.success(resourceid, getTimezone());
            case 16:
                return ReadResponse.success(resourceid, getSupportedBinding());
            default:
                return super.read(resourceid);
            }
        }

        @Override
        public ExecuteResponse execute(int resourceid, String params) {
            System.out.println("Execute on Device resource " + resourceid);
            if (params != null && params.length() != 0)
                System.out.println("\t params " + params);
            return ExecuteResponse.success();
        }

        @Override
        public WriteResponse write(int resourceid, LwM2mResource value) {
            System.out.println("Write on Device Resource " + resourceid + " value " + value);
            switch (resourceid) {
            case 13:
                return WriteResponse.notFound();
            case 14:
                setUtcOffset((String) value.getValue());
                fireResourcesChange(resourceid);
                return WriteResponse.success();
            case 15:
                setTimezone((String) value.getValue());
                fireResourcesChange(resourceid);
                return WriteResponse.success();
            default:
                return super.write(resourceid, value);
            }
        }

        private String getManufacturer() {
            return "Leshan Example Device";
        }

        private String getModelNumber() {
            return "Model 500";
        }

        private String getSerialNumber() {
            return "LT-500-000-0001";
        }

        private String getFirmwareVersion() {
            return "1.0.0";
        }

        private long getErrorCode() {
            return 0;
        }

        private int getBatteryLevel() {
            final Random rand = new Random();
            return rand.nextInt(100);
        }

        private int getMemoryFree() {
            final Random rand = new Random();
            return rand.nextInt(50) + 114;
        }

        private Date getCurrentTime() {
            return new Date();
        }

        private String utcOffset = new SimpleDateFormat("X").format(Calendar.getInstance().getTime());;

        private String getUtcOffset() {
            return utcOffset;
        }

        private void setUtcOffset(String t) {
            utcOffset = t;
        }

        private String timeZone = TimeZone.getDefault().getID();

        private String getTimezone() {
            return timeZone;
        }

        private void setTimezone(String t) {
            timeZone = t;
        }

        private String getSupportedBinding() {
            return "U";
        }
    }

    public static class Location extends BaseInstanceEnabler {
        private Random random;
        private float latitude;
        private float longitude;
        private Date timestamp;

        public Location() {
            random = new Random();
            latitude = Float.valueOf(random.nextInt(180));
            longitude = Float.valueOf(random.nextInt(360));
            timestamp = new Date();
        }

        @Override
        public ReadResponse read(int resourceid) {
            System.out.println("Read on Location Resource " + resourceid);
            switch (resourceid) {
            case 0:
                return ReadResponse.success(resourceid, getLatitude());
            case 1:
                return ReadResponse.success(resourceid, getLongitude());
            case 5:
                return ReadResponse.success(resourceid, getTimestamp());
            default:
                return super.read(resourceid);
            }
        }

        public void moveLocation(String nextMove) {
            switch (nextMove.charAt(0)) {
            case 'w':
                moveLatitude(1.0f);
                break;
            case 'a':
                moveLongitude(-1.0f);
                break;
            case 's':
                moveLatitude(-1.0f);
                break;
            case 'd':
                moveLongitude(1.0f);
                break;
            }
        }

        private void moveLatitude(float delta) {
            latitude = latitude + delta;
            timestamp = new Date();
            fireResourcesChange(0, 5);
        }

        private void moveLongitude(float delta) {
            longitude = longitude + delta;
            timestamp = new Date();
            fireResourcesChange(1, 5);
        }

        public String getLatitude() {
            return Float.toString(latitude - 90.0f);
        }

        public String getLongitude() {
            return Float.toString(longitude - 180.f);
        }

        public Date getTimestamp() {
            return timestamp;
        }
    }

    public static class Display extends BaseInstanceEnabler {
        private String text = "";

        @Override
        public ReadResponse read(int resourceid) {
            System.out.println("Read on Display Resource " + resourceid);
            switch (resourceid) {
            case 5527:
                return ReadResponse.success(resourceid, text);
            case 5528:
                return ReadResponse.success(resourceid, 0);
            case 5529:
                return ReadResponse.success(resourceid, 0);
            default:
                return super.read(resourceid);
            }
        }

        @Override
        public WriteResponse write(int resourceid, LwM2mResource value) {
            System.out.println("Write on Display Resource " + resourceid + " value " + value);
            switch (resourceid) {
            case 5527:
                if (((String) value.getValue()).equals("red") || ((String) value.getValue()).equals("orange")
                        || ((String) value.getValue()).equals("green")) {
                    text = (String) value.getValue();
                    try {
                        Process p = Runtime.getRuntime().exec("python /home/pi/" + text + ".py");
                        /*
                         * BufferedReader stdInput = new BufferedReader(new InputStreamReader(p.getInputStream()));
                         * 
                         * BufferedReader stdError = new BufferedReader(new InputStreamReader(p.getErrorStream()));
                         * System.out.println("Here is the standard output of the command:\n"); String s = null; while
                         * ((s = stdInput.readLine()) != null) { System.out.println(s); }
                         * 
                         * // read any errors from the attempted command System.out.println(
                         * "Here is the standard error of the command (if any):\n"); while ((s = stdError.readLine()) !=
                         * null) { System.out.println(s); }
                         */

                    } catch (IOException e) {
                        // TODO Auto-generated catch block
                        e.printStackTrace();
                    }
                    return WriteResponse.success();
                }
                return WriteResponse.badRequest("not Allowed");

            default:
                return super.write(resourceid, value);
            }
        }

    }

    public static class Joystick extends BaseInstanceEnabler {
        int counter = 0;
        int x = -100;
        int y = 0;
        int z = -100;

        @Override
        public ReadResponse read(int resourceid) {
            System.out.println("Read on Joystick Resource " + resourceid);
            switch (resourceid) {
            case 5500:
                return ReadResponse.success(resourceid, true);
            case 5501:
                return ReadResponse.success(resourceid, counter);
            case 5702:
                return ReadResponse.success(resourceid, x);
            case 5703:
                return ReadResponse.success(resourceid, y);
            case 5704:
                return ReadResponse.success(resourceid, z);
            default:
                return super.read(resourceid);
            }
        }

        public void sety(int y) {
            this.y = y;
            fireResourcesChange(5703);
        }
    }

    public static class ParkingSpot extends BaseInstanceEnabler {
        String state = "free";
        String vehicleId = "";
        double billingRate = 0.01;
        private int groupNr;

        public ParkingSpot() {
            super();
        }

        public ParkingSpot(int groupNr) {
            super();
            this.groupNr = groupNr;
        }

        @Override
        public ReadResponse read(int resourceid) {
            System.out.println("Read on ParkingSpot Resource " + resourceid);
            switch (resourceid) {
            case 32800:
                return ReadResponse.success(resourceid, "Parking-Spot-" + groupNr);
            case 32801:
                return ReadResponse.success(resourceid, state);
            case 32802:
                return ReadResponse.success(resourceid, vehicleId);
            case 32803:
                return ReadResponse.success(resourceid, billingRate);
            default:
                return super.read(resourceid);
            }
        }

        @Override
        public WriteResponse write(int resourceid, LwM2mResource value) {
            System.out.println("Write on ParkingSpot Resource " + resourceid + " value " + value);
            switch (resourceid) {
            case 32801:
                if (((String) value.getValue()).equals("free") || ((String) value.getValue()).equals("reserved")
                        || ((String) value.getValue()).equals("occupied")) {
                    state = (String) value.getValue();
                    return WriteResponse.success();
                }
                return WriteResponse.badRequest("not Allowed");
            case 32802:
                vehicleId = (String) value.getValue();
                return WriteResponse.success();
            case 32803:
                System.out.println("test");
                System.out.println(":" + (double) value.getValue() + ":");
                if (((Double) value.getValue()) >= 0) {
                    billingRate = (double) value.getValue();
                    return WriteResponse.success();
                }
                return WriteResponse.badRequest("not Allowed");
            default:
                return super.write(resourceid, value);
            }
        }
    }
}
