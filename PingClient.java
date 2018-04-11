import java.io.*;
import java.net.*;
import java.util.*;

public class PingClient {

    /**
     * Sets Timeout time after a Ping is sent.
     */
    private static class TimeOut extends Thread{
        int timeout;
        int sequenceNumber;
        ArrayList<Boolean>timeoutAL;

        public TimeOut(int timeout, int sequenceNumber, ArrayList<Boolean>timeoutAL){
            this.timeout = timeout;
            this.sequenceNumber = sequenceNumber;
            this.timeoutAL = timeoutAL;
        }
        // Sets array spot at ping position to false when timeout time is reached
        public void run(){
            try{
                // Random obscure port to host timeout session
                DatagramSocket soc = new DatagramSocket(25487 +sequenceNumber);

                soc.setSoTimeout(timeout);
                DatagramPacket response = new DatagramPacket(new byte[1024], 1024);
                soc.receive(response);

            }catch(IOException ex){
                timeoutAL.set(sequenceNumber-1,false);
            }
        }
    }

    /**
     * Sending Ping Thread
     */
    private static class SendMessage extends  Thread {

        InetAddress server;
        int port;
        int period;
        int sequenceNumber;
        int count;
        int timeout;
        DatagramSocket socket;
        ArrayList<Long>startTimeAL;
        ArrayList<Boolean>timeoutAL;

        public SendMessage(InetAddress server, int port, int period, int sequenceNumber, int count, DatagramSocket socket, int timeout,
                           ArrayList<Long>startTimeAL, ArrayList<Boolean>timeoutAL){
            this.server = server;
            this.port = port;
            this.period = period;
            this.sequenceNumber = sequenceNumber;
            this.count = count;
            this.socket = socket;
            this.timeout = timeout;
            this.startTimeAL =startTimeAL;
            this.timeoutAL = timeoutAL;
        }
        // Continuously send Ping until count is reached
        public void run() {

            boolean first = true;
            while (sequenceNumber <= count) {
                // Current Timestamp
                Date now = new Date();
                long msgSend = now.getTime();
                startTimeAL.add(msgSend);

                // Create string to send, and transfer i to a Byte Array
                String s = "PING " + sequenceNumber + " " + msgSend + " \n";
                byte[] buff;
                buff = s.getBytes();

                // Create a datagram packet to send as an UDP packet.
                DatagramPacket ping = new DatagramPacket(buff, buff.length, server, port);

                // Send datagram to server with delay period
                if(!first) {
                    try {
                        Thread.sleep(period);
                    } catch (InterruptedException ex) {}
                }
                try {
                    socket.send(ping);
                    TimeOut to = new TimeOut(timeout,sequenceNumber,timeoutAL);
                    to.start();
                }catch(IOException ex){System.out.println("Packet Not Sent");}
                sequenceNumber++;
                first = false;
            }
        }
    }

    /**
     * Receiving Message Thread
     */
    private static class ReceiveMessage extends Thread {

        InetAddress server;
        DatagramSocket socket;
        HashMap<String, Integer> hm = new HashMap<>();
        int period;
        int timeout;
        int sequenceNumber;
        int count;
        ArrayList<Long>startTimeAL;
        ArrayList<Boolean>timeoutAL;

        public ReceiveMessage(InetAddress server, DatagramSocket socket, HashMap<String, Integer>hm,int period,
                              int timeout,int sequenceNumber, int count, ArrayList<Long>st, ArrayList<Boolean> timeoutAL) {
            this.server = server;
            this.socket = socket;
            this.hm = hm;
            this.period = period;
            this.timeout = timeout;
            this.sequenceNumber = sequenceNumber;
            this.count = count;
            this.startTimeAL = st;
            this.timeoutAL = timeoutAL;
        }
        /**
         * Listens to port number: port + 1
         */
        public void run() {

            int receivedMessage = 0;
            boolean first = true;

            while (receivedMessage < count) {
                // Try to receive packets. Dropped packets will be caught.
                try {

                    // Setup timeout for when packets not received
                    socket.setSoTimeout(timeout);

                    if(!timeoutAL.get(sequenceNumber-1)) {
                        socket.setSoTimeout(1);
                    }
                    // Set up receiving UDP packet
                    DatagramPacket response = new DatagramPacket(new byte[1024], 1024);
                    socket.receive(response);
                    int i = getPacketNumber(response) - 1;

                    // Create new timestamp
                    Date now = new Date();
                    long msgReceived = now.getTime();
                    // Print the packet and the delay

                    // Prints the received message and updates the HashMap

                    long delayTime = msgReceived - startTimeAL.get(i) - period;

                    //first message does not contain waiting period;

                    if(receivedMessage == 0 && i != 0){
                        delayTime -= period;
                    }

                    if(first){
                        delayTime += period;
                    }

                    if(timeoutAL.get(i)) {
                        hm = printData(response, delayTime, hm, period);
                    } else{
                        // Update transmitted with missing packet
                        hm.put("transmitted", hm.get("transmitted") + 1);
                    }
                    // Offset the first round
                    first = false;

                } catch (Exception e) {
                    // Print which packet has timed out
                    //System.out.println("Timeout for packet " + sequenceNumber);

                    // Update transmitted with missing packet
                    hm.put("transmitted", hm.get("transmitted") + 1);
                }
                // Readies the next package to be sent
                receivedMessage++;
                sequenceNumber++;
            }

            Date now = new Date();
            long finished = now.getTime();
            finished = finished - startTimeAL.get(0);
            hm.put("truetime",(int)finished);

            // Min gets set to 0 if all packets miss.
            if (hm.get("min") == 99999999) {
                hm.put("min", 0);
            }
            // Prints the final summary
            printDataSummary(hm, server);
        }
    }

    public static void main(String[] args) throws Exception
    {
        // Initialize command-line arguments
        InetAddress server = InetAddress.getLocalHost();
        int port = -1;
        int count = -1;
        int period = -1;
        int timeout = -1;

        // Process command-line arguments
        for (String arg : args) {

            String[] splitArg = arg.split("=");

            if (splitArg.length == 2 && splitArg[0].equals("--server_ip")) {
                server = InetAddress.getByName(splitArg[1]);
            } else if (splitArg.length == 2 && splitArg[0].equals("--server_port")) {
                port = Integer.parseInt(splitArg[1]);
            } else if (splitArg.length == 2 && splitArg[0].equals("--count")) {
                count = Integer.parseInt(splitArg[1]);
            } else if (splitArg.length == 2 && splitArg[0].equals("--period")) {
                period = Integer.parseInt(splitArg[1]);
            } else if (splitArg.length == 2 && splitArg[0].equals("--timeout")) {
                timeout = Integer.parseInt(splitArg[1]);
            } else {
                System.err.println("Usage: java PingClient --server_ip=<ip> [--server_port=<port>] [--count=<count>]" +
                        " [--period=<period>] [--timeout=<timeout>]");
                return;
            }
        }
        // Create a datagram socket for sending and receiving UDP packets
        // through the port specified on the command line.
        DatagramSocket socket = new DatagramSocket(port+1); // make sure to use a different port

        System.out.println("PING " + server.getHostAddress());

        // Initialize first sequence
        int sequenceNumber = 1;

        // Create HashMap to store final values
        HashMap <String,Integer> hm = createDataSummaryHM();

        // Create ArrayList for starting time set
        ArrayList<Long> startTimeAL = new ArrayList<>();

        // Create ArrayList to determine Ping Sent

        // Create ArrayList for timeout
        ArrayList<Boolean>timeoutAL = new ArrayList<>();
        for(int i = 0; i < count; i ++){
            timeoutAL.add(true);
        }
        // Processing loop.
        SendMessage sm = new SendMessage(server,port,period,sequenceNumber,count,
                socket,timeout,startTimeAL,timeoutAL);

        ReceiveMessage rm = new ReceiveMessage(server, socket, hm, period, timeout,
                sequenceNumber, count, startTimeAL, timeoutAL);

        sm.start();     // Starting Send Message Thread
        rm.start();     // Starting Receive Message Thread
    }

    /**
     * Returns the packet number that was received.
     */
    private static int getPacketNumber(DatagramPacket request) {
        int i = 0;
        try{
            byte[] buf = request.getData();
            ByteArrayInputStream bais = new ByteArrayInputStream(buf);
            InputStreamReader isr = new InputStreamReader(bais);
            BufferedReader br = new BufferedReader(isr);
            String line = br.readLine();
            String[] splitted = line.split("\\s+");
            i = Integer.parseInt(splitted[1]);
        }catch(IOException ex){}
        return i;
    }

    /**
     * Print ping data to the standard output stream.
     */
    private static HashMap<String,Integer> printData(DatagramPacket request, long delayTime, HashMap<String,Integer> hm, int period) throws Exception
    {
        // Obtain references to the packet's array of bytes.
        byte[] buf = request.getData();

        // Wrap the bytes in a byte array input stream,
        // so that you can read the data as a stream of bytes.
        ByteArrayInputStream bais = new ByteArrayInputStream(buf);

        // Wrap the byte array output stream in an input stream reader,
        // so you can read the data as a stream of characters.
        InputStreamReader isr = new InputStreamReader(bais);

        // Wrap the input stream reader in a bufferred reader,
        // so you can read the character data a line at a time.
        // (A line is a sequence of chars terminated by any combination of \r and \n.)
        BufferedReader br = new BufferedReader(isr);

        // The message data is contained in a single line, so read this line.
        String line = br.readLine();

        // Update # transmitted
        hm.put("transmitted",hm.get("transmitted") +1);

        // Update # Received and total time.
        if(line.startsWith("PING")){
            line = "seq=" + line.substring(5,line.length()-1);

            hm.put("received",hm.get("received")+1);
            hm.put("time",hm.get("time")+(int)delayTime);


            // Update Min/Max
            if(delayTime < hm.get("min")){
                hm.put("min", (int)delayTime);
            }
            if(delayTime > hm.get("max")) {
                hm.put("max", (int) delayTime);
            }
        }
        String [] splitted = line.split("\\s+");
        // Print host address and data received from it.
        System.out.println("PONG " + request.getAddress().getHostAddress() + ": " + splitted[0] + " time=" + delayTime + " ms");

        return hm;
    }

    /**
     * Prints out the final data summary
     */
    private static void printDataSummary(HashMap<String,Integer> hm, InetAddress server){
        System.out.println("\n--- " + server.getHostAddress() + " ping statistics ---");

        int transmitted = hm.get("transmitted");
        int received = hm.get("received");
        int time = hm.get("time");              // sum of total time for all ping transit.
        int truetime = hm.get("truetime");      // total time spent from sending first ping and receiving last.
        double percentLoss;

        if(transmitted == 0){
            percentLoss = 100;
        }else{ percentLoss = 100 - (100 *received/transmitted);}

        String percentLossF = String.format("%.2f",percentLoss);

        double averageRTT;
        if(received == 0) {
            averageRTT = 0;
        }else{
            averageRTT = time/received;
        }

        System.out.println(transmitted + " transmitted, "
                + received + " received, "
                + percentLossF + "% loss, "
                + "time " + truetime + "ms");
        System.out.println("rtt min/avg/max = "
                + hm.get("min") + "/"
                + (int)averageRTT + "/"
                + hm.get("max") +" ms");

        System.exit(1);    // Stops the program once stats are printed
    }

    /**
     * Initializes hash map to be used for the Data Summary
     */
    private static HashMap<String,Integer> createDataSummaryHM(){
        HashMap<String,Integer>hm = new HashMap<>();
        hm.put("transmitted",0);
        hm.put("received",0);
        hm.put("time",0);
        hm.put("truetime",0);
        hm.put("min",99999999);
        hm.put("max",0);
        return hm;
    }
}
