import java.io.*;
import java.net.*;
import java.nio.ByteBuffer;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;


public class peerProcess {
    private static final char CHOKE = '0';
    private static final char UNCHOKE = '1';
    private static final char INTERESTED = '2';
    private static final char NOT_INTERESTED = '3';
    private static final char HAVE = '4';
    private static final char BITFIELD = '5';
    private static final char REQUEST = '6';
    private static final char PIECE = '7';
    private static int hostID;
    private static LinkedHashMap<Integer, PeerInfo> peers;
    private static byte[][] filePieces;
    private static Messages msg = new Messages();
    private static File log_file;
    private static Logs logs;
    private static ConcurrentHashMap<Integer, PeerConnection> peerConnections;
    private static PeerInfo thisPeer;
    private static CommonInfo common;
    private static int completedPeers = 0;
    private static File directory;

    public static void main(String[] args) {
        hostID = Integer.parseInt(args[0]);
        try {
            /*
            *   Create the file directory corresponding to this peer.
             */
            directory = new File("peer_" + hostID);
            if (directory.exists() == false) {
                directory.mkdir();
            }
            log_file = new File(System.getProperty("user.dir") + "/" + "log_peer_" + hostID + ".log");
            if (log_file.exists() == false)
                log_file.createNewFile();
            BufferedWriter writer = new BufferedWriter(new FileWriter(log_file.getAbsolutePath(), true));
            writer.flush();
            logs = new Logs(writer);
            /*
            *   Read PeerInfo.cfg file. Each line in this file contains details about a peer.
            *   Create PeerInfo object for each line in this file.
            *   Enter details of each peer in corresponding PeerInfo object.
            *   Put PeerInfo objects in a LinkedHashMap with their peerIDs as keys.
            *   LinkedHashMap is used to maintain the order of stored PeerInfo objects according to the file.
             */
            BufferedReader peerInfo = new BufferedReader(new FileReader("PeerInfo.cfg"));
            peers = new LinkedHashMap<>();
            for (Object line : peerInfo.lines().toArray()) {
                String[] parts = ((String) line).split(" ");
                PeerInfo peer = new PeerInfo();
                peer.setPeerID(Integer.parseInt(parts[0]));
                peer.setHostName(parts[1]);
                peer.setPortNumber(Integer.parseInt(parts[2]));
                peer.setHaveFile(Integer.parseInt(parts[3]));
                peers.put(peer.getPeerID(), peer);
            }
            peerInfo.close();

//            for(PeerInfo peer : peers.values()){
//                System.out.println(peer.getHost() + ":" + peer.getPort() + ":" + asdfasf peer.getHaveFile());
//            }

            /*
            *   Read Common.cfg file. Each line contains values of some variables.
            *   Create object of CommonInfo class to store the values of those variables in corresponding variables of CommonInfo object.
             */
            BufferedReader commonInfo = new BufferedReader(new FileReader("Common.cfg"));
            common = new CommonInfo();
            Object[] commonInfoLines = commonInfo.lines().toArray();
            common.setNumberOfPreferredNeighbors(Integer.parseInt(((String) commonInfoLines[0]).split(" ")[1]));
            common.setUnchokingInterval(Integer.parseInt(((String) commonInfoLines[1]).split(" ")[1]));
            common.setOptimisticUnchokingInterval(Integer.parseInt(((String) commonInfoLines[2]).split(" ")[1]));
            common.setFileName(((String) commonInfoLines[3]).split(" ")[1]);
            common.setFileSize(Integer.parseInt(((String) commonInfoLines[4]).split(" ")[1]));
            common.setPieceSize(Integer.parseInt(((String) commonInfoLines[5]).split(" ")[1]));
            commonInfo.close();

            /*
            *   Add the Bitfield to this peer.
            *   If this peer has file, all bits in Bitfield are set to 1, else they are set to 0.
            *   If this peer has file, new pieces of files are created.
            *   All those pieces are stored in array of pieces corresponding to their indices.
            *   Size of pieces is given in Common.cfg
            *   Number of pieces = Ceil(FileSize/PieceSize)
            *   Size of Bitfield = Number of pieces
             */
            thisPeer = peers.get(hostID);
            int fileSize = common.getFileSize();
            int pieceSize = common.getPieceSize();
            int numOfPieces = (int) Math.ceil((double) fileSize / pieceSize);
            filePieces = new byte[numOfPieces][];
            int bitfieldSize = numOfPieces;
            int[] bitfield = new int[bitfieldSize];
            if (thisPeer.getHaveFile() == 1) {
                completedPeers++;
                Arrays.fill(bitfield, 1);
                thisPeer.setBitfield(bitfield);
                //Dividing File into pieces and storing them into array of pieces.
                BufferedInputStream file = new BufferedInputStream(new FileInputStream(directory.getAbsolutePath() + "/" + common.getFileName()));
                byte[] fileBytes = new byte[fileSize];
                file.read(fileBytes);
                file.close();
                int part = 0;

                for (int counter = 0; counter < fileSize; counter += pieceSize) {
                    //byte[] pieceBytes = Arrays.copyOfRange(fileBytes, counter, counter + pieceSize);
                    if (counter + pieceSize <= fileSize)
                        filePieces[part] = Arrays.copyOfRange(fileBytes, counter, counter + pieceSize);
                    else
                        filePieces[part] = Arrays.copyOfRange(fileBytes, counter, fileSize);
//                    BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream("Common_" + part + ".cfg"));
//                    bos.write(pieces[part]);
//                    bos.close();
                    part++;
                    thisPeer.updateNumOfPieces();
                }
            } else {
                Arrays.fill(bitfield, 0);
                thisPeer.setBitfield(bitfield);
            }
            //System.out.println(thisPeer.getNumOfPieces());

            /*
            *   Connections are established with peers.
            *   There are two types of connections.
            *   0 - Peer makes connection with all the previous peers.
            *   1 - Peer accepts connection from all the new peers.
             */

            peerConnections = new ConcurrentHashMap<>();
            //unchokedPeers = new ArrayList<>();
            SendConnections sendConnections = new SendConnections();
            sendConnections.start();
            ReceiveConnections receiveConnections = new ReceiveConnections();
            receiveConnections.start();
            UnchokePeers unchokePeers = new UnchokePeers();
            unchokePeers.start();
            OptimisticUnchokePeer optimisticUnchokePeer = new OptimisticUnchokePeer();
            optimisticUnchokePeer.start();
        } catch (Exception e) {
//            e.printStackTrace();
        }
    }

    private static class SendConnections extends Thread{
        @Override
        public void run(){
            byte[] buffer = new byte[32];
            try{
                for(int id : peers.keySet()){
                    if(id == hostID)
                        break;
                    else{
                        PeerInfo connPeer = peers.get(id);
                        Socket connection = new Socket(connPeer.getHostName(), connPeer.getPortNumber());
                        DataOutputStream dataOutputStream = new DataOutputStream(connection.getOutputStream());
                        dataOutputStream.flush();
                        dataOutputStream.write(msg.getHandshakeMessage(hostID));
                        dataOutputStream.flush();
                        DataInputStream dataInputStream = new DataInputStream(connection.getInputStream());
                        dataInputStream.readFully(buffer);
                        int peerID = ByteBuffer.wrap(Arrays.copyOfRange(buffer, 28, 32)).getInt();
                        if(peerID != id)
                            connection.close();
                        else{
                            logs.connectionTo(hostID, id);
                            StringBuilder handshakeMsg = new StringBuilder();
                            handshakeMsg.append(new String(Arrays.copyOfRange(buffer, 0, 28)));
                            handshakeMsg.append(peerID);
                            System.out.println(handshakeMsg);
                            peerConnections.put(id, new PeerConnection(connection, id));
                        }
                    }
                }
            }
            catch(Exception e){
//                e.printStackTrace();
            }
        }
    }

    private static class ReceiveConnections extends Thread{
        @Override
        public void run(){
            byte[] buffer = new byte[32];
            try{
                ServerSocket serverSocket = new ServerSocket(thisPeer.getPortNumber());
                while(peerConnections.size() < peers.size() - 1){
                    Socket connection = serverSocket.accept();
                    DataInputStream dataInputStream = new DataInputStream(connection.getInputStream());
                    dataInputStream.readFully(buffer);
                    int peerID = ByteBuffer.wrap(Arrays.copyOfRange(buffer, 28, 32)).getInt();
                    logs.connectionFrom(hostID, peerID);
                    StringBuilder handshakeMsg = new StringBuilder();
                    handshakeMsg.append(new String(Arrays.copyOfRange(buffer, 0, 28)));
                    handshakeMsg.append(peerID);
                    System.out.println(handshakeMsg);
                    peerConnections.put(peerID, new PeerConnection(connection, peerID));
                    DataOutputStream dataOutputStream = new DataOutputStream(connection.getOutputStream());
                    dataOutputStream.flush();
                    dataOutputStream.write(msg.getHandshakeMessage(hostID));
                }
            }
            catch(Exception e){
//                e.printStackTrace();
            }
        }
    }
    public void yogi(){
        return;
    }
    private static class UnchokePeers extends Thread{
        @Override
        public void run(){
            while(completedPeers < peers.size()){
                ArrayList<Integer> connections = new ArrayList<>(peerConnections.keySet());
                int[] preferredNeighbors = new int[common.getNumberOfPreferredNeighbors()];
                if(thisPeer.getHaveFile() == 1) {
                    ArrayList<Integer> interestedPeers = new ArrayList<>();
                    for (int peer : connections) {
                        if(peerConnections.get(peer).isInterested())
                            interestedPeers.add(peer);
                    }
                    if (interestedPeers.size() > 0) {
                        if (interestedPeers.size() <= common.getNumberOfPreferredNeighbors()) {
                            for (Integer peer : interestedPeers) {
                                if(peerConnections.get(peer).isChoked()){
                                    peerConnections.get(peer).unchoke();
                                    peerConnections.get(peer).sendMessage(UNCHOKE);
                                }
                            }
                        } else {
                            Random r = new Random();
                            for (int i = 0; i < common.getNumberOfPreferredNeighbors(); i++) {
                                preferredNeighbors[i] = (interestedPeers.remove(Math.abs(r.nextInt() % interestedPeers.size())));
                            }
                            for (int peer : preferredNeighbors) {
                                if(peerConnections.get(peer).isChoked()){
                                    peerConnections.get(peer).unchoke();
                                    peerConnections.get(peer).sendMessage(UNCHOKE);
                                }
                            }
                            for (Integer peer : interestedPeers) {
                                if(!peerConnections.get(peer).isChoked() && !peerConnections.get(peer).isOptimisticallyUnchoked()){
                                    peerConnections.get(peer).choke();
                                    peerConnections.get(peer).sendMessage(CHOKE);
                                }
                            }
                        }
                    }
                }
                else{
                    ArrayList<Integer> interestedPeers = new ArrayList<>();
                    int counter = 0;
                    for (int peer : connections) {
                        if(peerConnections.get(peer).isInterested() && peerConnections.get(peer).getDownloadRate() >= 0)
                            interestedPeers.add(peer);
                    }
                    if(interestedPeers.size() <= common.getNumberOfPreferredNeighbors()){
                        for(int peer : interestedPeers){
                            preferredNeighbors[counter++] = peer;
                            if(peerConnections.get(peer).isChoked()){
                                peerConnections.get(peer).unchoke();
                                peerConnections.get(peer).sendMessage(UNCHOKE);
                            }
                        }
                    }
                    else {
                        for (int i = 0; i < common.getNumberOfPreferredNeighbors(); i++) {
                            int max = interestedPeers.get(0);
                            for(int j = 1; j < interestedPeers.size(); j++){
                                if(peerConnections.get(max).getDownloadRate() <= peerConnections.get(interestedPeers.get(j)).getDownloadRate()){
                                    max = interestedPeers.get(j);
                                }
                            }
                            if(peerConnections.get(max).isChoked()) {
                                peerConnections.get(max).unchoke();
                                peerConnections.get(max).sendMessage(UNCHOKE);
                            }
                            preferredNeighbors[i] = max;
                            interestedPeers.remove(Integer.valueOf(max));
                        }
                        for (Integer peer : interestedPeers) {
                            if(!peerConnections.get(peer).isChoked() && !peerConnections.get(peer).isOptimisticallyUnchoked()){
                                peerConnections.get(peer).choke();
                                peerConnections.get(peer).sendMessage(CHOKE);
                            }
                        }
                    }
                }
                logs.changePreferredNeighbors(thisPeer.getPeerID(), preferredNeighbors);
                try{
                    Thread.sleep(common.getUnchokingInterval() * 1000);
                }
                catch(Exception e){
//                    e.printStackTrace();
                }
            }
            try{
                Thread.sleep(5000);
            }
            catch(Exception e){

            }
            System.exit(0);
        }
    }

    private static class OptimisticUnchokePeer extends Thread{
        @Override
        public void run(){
            while (completedPeers < peers.size()) {
                ArrayList<Integer> connections = new ArrayList<>(peerConnections.keySet());
                ArrayList<Integer> interested = new ArrayList<>();
                for(int connection : connections){
                    if(peerConnections.get(connection).isInterested()){
                        interested.add(connection);
                    }
                }
                if(interested.size() > 0){
                    Random r = new Random();
                    int randomNumber = Math.abs(r.nextInt() % interested.size());
                    int connection = interested.get(randomNumber);
                    peerConnections.get(connection).unchoke();
                    peerConnections.get(connection).sendMessage(UNCHOKE);
                    peerConnections.get(connection).optimisticallyUnchoke();
                    logs.changeOptimisticallyUnchokedNeighbor(thisPeer.getPeerID(), peerConnections.get(connection).getPeerID());
                    try {
                        Thread.sleep(common.getOptimisticUnchokingInterval() * 1000);
                        peerConnections.get(connection).optimisticallyChoke();
                    }
                    catch(Exception e){
//                        e.printStackTrace();
                    }
                }
            }
            try{
                Thread.sleep(5000);
            }
            catch(Exception e){

            }
            System.exit(0);
        }
    }

    private static class PeerConnection{
        private Socket connection;
        private int peerID;
        private boolean interested = false;
        private boolean choked = true;
        private boolean optimisticallyUnchoked = false;
        private double downloadRate = 0;

        public double getDownloadRate() {
            return downloadRate;
        }

        public void setDownloadRate(double rate) {
            this.downloadRate = rate;
        }

        public boolean isOptimisticallyUnchoked() {
            return optimisticallyUnchoked;
        }

        public void optimisticallyUnchoke() {
            optimisticallyUnchoked = true;
        }
        public void optimisticallyChoke(){
            optimisticallyUnchoked = false;
        }

        public boolean isInterested() {
            return interested;
        }

        public void setInterested() {
            interested = true;
        }
        public void setNotInterested(){
            interested = false;
        }

        public boolean isChoked() {
            return choked;
        }

        public void choke() {
            choked = true;
        }
        public void unchoke(){
            choked = false;
        }

        public int getPeerID(){
            return peerID;
        }

        public Socket getConnection() {
            return connection;
        }

        public PeerConnection(Socket conn, int id){
            connection = conn;
            peerID = id;
            (new ReaderThread(this)).start();
        }

        public void sendMessage(char type){
            try{
                DataOutputStream dataOutputStream = new DataOutputStream(connection.getOutputStream());
                dataOutputStream.flush();
                switch (type){
                    case CHOKE:
                        dataOutputStream.write(msg.getChokeMessage());
                        break;
                    case UNCHOKE:
                        dataOutputStream.write(msg.getUnchokeMessage());
                        break;
                    case INTERESTED:
                        dataOutputStream.write(msg.getInterestedMessage());
                        break;
                    case NOT_INTERESTED:
                        dataOutputStream.write(msg.getNotInterestedMessage());
                        break;
                    case BITFIELD:
                        dataOutputStream.write(msg.getBitfieldMessage(thisPeer.getBitfield()));
                        break;
                    default:
                        break;
                }
                dataOutputStream.flush();
            }
            catch(Exception e){
//                e.printStackTrace();
            }
        }

        public void sendMessage(char type, int index){
            try{
                DataOutputStream dataOutputStream = new DataOutputStream(connection.getOutputStream());
                dataOutputStream.flush();
                switch (type){
                    case HAVE:
                        dataOutputStream.write(msg.getHaveMessage(index));
                        break;
                    case REQUEST:
                        dataOutputStream.write(msg.getRequestMessage(index));
                        break;
                    case PIECE:
                        dataOutputStream.write(msg.getPieceMessage(index, filePieces[index]));
                        break;
                    default:
                        break;
                }
                dataOutputStream.flush();
            }
            catch(Exception e){
//                e.printStackTrace();
            }
        }

        public void compareBitfield(int[] thisPeerBitfield, int[] connectedPeerBitfield, int len){
            int i;
            for(i = 0; i < len; i++){
                if(thisPeerBitfield[i] == 0 && connectedPeerBitfield[i] == 1){
                    sendMessage(INTERESTED);
                    break;
                }
            }
            if(i == len)
                sendMessage(NOT_INTERESTED);
        }

        public void getPieceIndex(int[] thisPeerBitfield, int[] connectedPeerBitfield, int len){
            ArrayList<Integer> indices = new ArrayList<>();
            int i;
            for(i = 0; i < len; i++){
                if(thisPeerBitfield[i] == 0 && connectedPeerBitfield[i] == 1){
                    indices.add(i);
                }
            }
            Random r = new Random();
            if(indices.size() > 0){
                int index = indices.get(Math.abs(r.nextInt() % indices.size()));
                sendMessage(REQUEST, index);
            }
        }

        public void checkCompleted(){
            int counter = 0;
            for(int bit : thisPeer.getBitfield()){
                if(bit == 1)
                    counter++;
            }
            if(counter == thisPeer.getBitfield().length){
                logs.downloadCompleted(thisPeer.getPeerID());
                counter = 0;
                byte[] merge = new byte[common.getFileSize()];
                for(byte[] piece : filePieces){
                    for(byte b : piece){
                        merge[counter] = b;
                        counter++;
                    }
                }
                try {
                    FileOutputStream file = new FileOutputStream(directory.getAbsolutePath() + "/" + common.getFileName());
                    BufferedOutputStream bos = new BufferedOutputStream(file);
                    bos.write(merge);
                    bos.close();
                    file.close();
                    System.out.println("File Download Completed.");
                    thisPeer.setHaveFile(1);
                    completedPeers++;
                }
                catch (IOException e) {
//                    e.printStackTrace();
                }
            }
        }

        private static class ReaderThread extends Thread{
            private PeerConnection peer;

            public ReaderThread(PeerConnection peer){
                this.peer = peer;
            }

            @Override
            public void run(){
                double startTime;
                double endTime;
                synchronized (this)
                {
                    try{
                        DataInputStream dataInputStream = new DataInputStream(peer.getConnection().getInputStream());
                        peer.sendMessage(BITFIELD);
                        while(completedPeers < peers.size()){
                            int msgLength = dataInputStream.readInt();
                            byte[] buffer = new byte[msgLength];
                            startTime = (double)System.nanoTime() / 100000000;
                            dataInputStream.readFully(buffer);
                            endTime = (double)System.nanoTime() / 100000000;
                            char msgType = (char)buffer[0];
                            byte[] msg = new byte[msgLength - 1];
                            int counter = 0;
                            for(int i = 1; i < msgLength; i++){
                                msg[counter] = buffer[i];
                                counter++;
                            }
                            int index;
                            int bits;
                            switch (msgType){
                                case CHOKE:
                                    logs.choked(thisPeer.getPeerID(), peer.peerID);
                                    peer.choke();
                                    break;
                                case UNCHOKE:
                                    peer.unchoke();
                                    logs.unchoked(thisPeer.getPeerID(), peer.peerID);
                                    peer.getPieceIndex(thisPeer.getBitfield(), peers.get(peer.peerID).getBitfield(), thisPeer.getBitfield().length);
                                    break;
                                case INTERESTED:
                                    logs.receiveInterested(thisPeer.getPeerID(), peer.peerID);
                                    peer.setInterested();
                                    break;
                                case NOT_INTERESTED:
                                    logs.receiveNotInterested(thisPeer.getPeerID(), peer.peerID);
                                    peer.setNotInterested();
                                    if(!peer.isChoked()){
                                        peer.choke();
                                        peer.sendMessage(CHOKE);
                                    }
                                    break;
                                case HAVE:
                                    index = ByteBuffer.wrap(msg).getInt();
                                    peers.get(peer.getPeerID()).updateBitfield(index);
                                    bits = 0;
                                    for(int x : peers.get(peer.getPeerID()).getBitfield()){
                                        if(x == 1)
                                            bits++;
                                    }
                                    if(bits == thisPeer.getBitfield().length){
                                        peers.get(peer.getPeerID()).setHaveFile(1);
                                        completedPeers++;
                                    }
                                    peer.compareBitfield(thisPeer.getBitfield(), peers.get(peer.getPeerID()).getBitfield(), thisPeer.getBitfield().length);
                                    logs.receiveHave(thisPeer.getPeerID(), peer.getPeerID(), index);
                                    break;
                                case BITFIELD:
                                    int[] bitfield = new int[msg.length/4];
                                    counter = 0;
                                    for(int i = 0; i < msg.length; i += 4){
                                        bitfield[counter] = ByteBuffer.wrap(Arrays.copyOfRange(msg, i, i + 4)).getInt();
                                        counter++;
                                    }
                                    peers.get(peer.peerID).setBitfield(bitfield);
                                    bits = 0;
                                    for(int x : peers.get(peer.getPeerID()).getBitfield()){
                                        if(x == 1)
                                            bits++;
                                    }
                                    if(bits == thisPeer.getBitfield().length){
                                        peers.get(peer.getPeerID()).setHaveFile(1);
                                        completedPeers++;
                                    }
                                    else{
                                        peers.get(peer.getPeerID()).setHaveFile(0);
                                    }
                                    peer.compareBitfield(thisPeer.getBitfield(), bitfield, bitfield.length);
                                    break;
                                case REQUEST:
                                    peer.sendMessage(PIECE, ByteBuffer.wrap(msg).getInt());
                                    break;
                                case PIECE:
                                    index = ByteBuffer.wrap(Arrays.copyOfRange(msg, 0, 4)).getInt();
                                    counter = 0;
                                    filePieces[index] = new byte[msg.length - 4];
                                    for(int i = 4; i < msg.length; i++){
                                        filePieces[index][counter] = msg[i];
                                        counter++;
                                    }
                                    thisPeer.updateBitfield(index);
                                    thisPeer.updateNumOfPieces();
                                    if(!peer.isChoked()){
                                        peer.getPieceIndex(thisPeer.getBitfield(), peers.get(peer.peerID).getBitfield(), thisPeer.getBitfield().length);
                                    }
                                    double rate = ((double)(msg.length + 5) / (endTime - startTime));
                                    if(peers.get(peer.getPeerID()).getHaveFile() == 1){
                                        peer.setDownloadRate(-1);
                                    }
                                    else{
                                        peer.setDownloadRate(rate);
                                    }
                                    logs.downloadingPiece(thisPeer.getPeerID(), peer.getPeerID(), index, thisPeer.getNumOfPieces());
                                    int downloaded = (thisPeer.getNumOfPieces() * 100) / (int)Math.ceil((double)common.getFileSize()/common.getPieceSize());
                                    StringBuffer sb = new StringBuffer();
                                    sb.append("\r").append("Downloaded: ");
                                    sb.append(downloaded).append("%").append(" Number of Pieces: ").append(thisPeer.getNumOfPieces());
                                    System.out.print(sb);
                                    peer.checkCompleted();
                                    for(int connection : peerConnections.keySet()){
                                        peerConnections.get(connection).sendMessage(HAVE, index);
                                    }
                                    break;
                                default:
                                    break;
                            }
                        }
                        Thread.sleep(5000);
                        System.exit(0);
                    }
                    catch(Exception e){
//                        e.printStackTrace();
                    }
                }
            }
        }
    }
}
