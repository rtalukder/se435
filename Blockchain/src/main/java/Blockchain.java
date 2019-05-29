/* CDE: The JAXB libraries and utilities*/

import com.sun.security.ntlm.Server;
import org.omg.PortableServer.POA;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.bind.Unmarshaller;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.crypto.Data;
import java.io.StringWriter;
import java.io.StringReader;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.net.*;
import java.security.*;
import java.security.spec.X509EncodedKeySpec;
import java.util.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.regex.*;
import java.text.*;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

/**
 * Class: CSC 435 - Blockchain Server
 * Author: Raquib Talukder
 * Sources: http://condor.depaul.edu/elliott/se435/hw/programs/Blockchain/program-block.html
 *          https://www.baeldung.com/udp-in-java
 *          https://www.baeldung.com/java-broadcast-multicast
 *
 **/

class BlockchainPublicKeysServer implements Runnable {
    DatagramSocket socket;
    int PID;
    HashMap<String,PublicKey> PIDPubKeys;

    public BlockchainPublicKeysServer(int blockchainPublicKeysPort, int PID){
        this.PID = PID;

        try {
            DatagramSocket datagramSocket = new DatagramSocket(blockchainPublicKeysPort);
            this.socket = datagramSocket;
        }
        catch (Exception exception){
            System.out.println("BlockchainPubKeysServer: Exception caught");
        }
        this.PIDPubKeys = new HashMap<>();
    }

    public int getHashSize(){
        return this.PIDPubKeys.size();
    }

    public PublicKey getPubKey(String PID){
        return this.PIDPubKeys.get(PID);
    }

    @Override
    public void run() {
        // source for UDP Datagram packets: https://www.baeldung.com/udp-in-java
        // DatagramPacket is used to receive messages on speficied port
        // DatagramSocket is created in the constructor and is associated with the PID
        final byte[] buffer = new byte[2048];
        DatagramPacket packet = new DatagramPacket(buffer, buffer.length);

        while (true) {
            try {
                this.socket.receive(packet);
            } catch (IOException exception) {
                System.out.println("IOE exception caught.");
            }

            String pubKeyPacket = new String(packet.getData(), 0, packet.getLength());
            String[] splitString = pubKeyPacket.split("----");

            try {
                // utility code from Professor Elliott
                byte[] pubKeyDecoded  = Base64.getDecoder().decode(splitString[2]);
                X509EncodedKeySpec keySpec = new X509EncodedKeySpec(pubKeyDecoded);
                KeyFactory pubKeyFactory = KeyFactory.getInstance("RSA");
                PublicKey pubKeyToHash = pubKeyFactory.generatePublic(keySpec);
                this.PIDPubKeys.put(splitString[1], pubKeyToHash);
            }
            catch (Exception exception){
                System.out.println("BlockchainPubKeysServer - Run: Exception caught while unpacking public key");
            }
        }
    }
}

class BlockchainVerifierServer extends Thread {
    BlockchainPublicKeysServer blockchainPublicKeysServer;
    int PID;
    DatagramSocket socket;
    final BlockingQueue<BlockRecord> unverifiedBlocksList;
    LinkedList<BlockRecord> blockchainList;
    ArrayList<String> blockIDList;

    // int blockchainVerifierPort, int PID, BlockchainPublicKeysServer blockchainPublicKeysServer
    public BlockchainVerifierServer(int PID, BlockchainPublicKeysServer blockchainPublicKeysServer){
        this.blockchainPublicKeysServer = blockchainPublicKeysServer;
        this.PID = PID;
        this.unverifiedBlocksList = new LinkedBlockingQueue<>();
        this.blockchainList = new LinkedList<>();
        this.blockIDList = new ArrayList<>();
        // dummy data for the first block
        this.blockIDList.add("77df263f49123356d28a4a8715d25bf5b980beeeb503cab46ea61ac9f3320eda");
        BlockRecord dummyRecord = new BlockRecord();
        dummyRecord.setABlockID("236e1834-5a02-46a3-974e-58e90bf87d2b");
        dummyRecord.setASHA256String("77df263f49123356d28a4a8715d25bf5b980beeeb503cab46ea61ac9f3320eda");
        dummyRecord.setABlockID("0");
        this.blockchainList.add(dummyRecord);
    }

    public void AddBlock(String XMLblock){
        BlockRecord convertedBlock = this.ConvertBlock(XMLblock);
        try {
            if (convertedBlock != null) {
                this.unverifiedBlocksList.add(convertedBlock);
            }
        } catch (Exception exception){
            exception.printStackTrace();
            System.out.println("AddBlock: Exception thrown");
        }
    }

    public BlockRecord ConvertBlock(String XMLblock){
        BlockRecord convertedBlock = null;
        try {
            StringReader reader = new StringReader(XMLblock);
            JAXBContext jaxbContext = JAXBContext.newInstance(BlockRecord.class);
            Unmarshaller jaxbUnmarshaller = jaxbContext.createUnmarshaller();
            convertedBlock = (BlockRecord) jaxbUnmarshaller.unmarshal(reader);

        }
        catch (Exception exception){
            System.out.println("ConvertBlock: Exception caught.");
        }
        return convertedBlock;
    }

    // utility function from Professor Elliott
    public boolean VerifySignature(byte[] data, PublicKey key, byte[] sig){
        try {
            Signature signer = Signature.getInstance("SHA1withRSA");
            signer.initVerify(key);
            signer.update(data);
            return (signer.verify(sig));
        }
        catch (Exception excpetion){
            excpetion.printStackTrace();
            System.out.println("VerifySignature: Exception caught.");
            return false;
        }
    }

    @Override
    public void run(){
        while (true) {
            if(!(unverifiedBlocksList.isEmpty())){
                BlockRecord workingBlock = unverifiedBlocksList.remove();

                // find out who signed the block
                byte[] decodedBlockSignature = Base64.getDecoder().decode(workingBlock.getASignedBlockUUID());
                String[] splitPID = workingBlock.getAPID().split(" ");
                PublicKey getPIDPubKey = this.blockchainPublicKeysServer.getPubKey(splitPID[1]);
                boolean verifiedSignatureBlockID = this.VerifySignature(workingBlock.getABlockID().getBytes(), getPIDPubKey, decodedBlockSignature);
                System.out.println(verifiedSignatureBlockID);

//                if(!verifiedSignatureBlockID){
//                    continue;
//                }

            }
        }

    }
}

class UnverifiedBlockchainServer extends Thread {
    BlockchainVerifierServer blockchainVerifierServer;
    DatagramSocket socket;
    KeyPair pubPrivKey;
    int PID;

    public UnverifiedBlockchainServer(int unverifiedBlockchainServerPort, BlockchainPublicKeysServer blockchainPublicKeysServer, int verifiedBlockchainServerPort, int PID) {
        this.PID = PID;
        this.blockchainVerifierServer = new BlockchainVerifierServer(PID, blockchainPublicKeysServer);

        try {
            this.pubPrivKey = GenerateKeyPair();
            DatagramSocket datagramSocket = new DatagramSocket(unverifiedBlockchainServerPort);
            this.socket = datagramSocket;
        }
        catch (Exception exception){
            System.out.println("UnverifiedBlockchainServer: Exception caught");
        }
        if (PID == 2){
            MulticastEvent("PUBKEYS");
        }
    }

    @Override
    public void run() {
        // source for UDP Datagram packets: https://www.baeldung.com/udp-in-java
        // DatagramPacket is used to receive messages on speficied port
        // DatagramSocket is created in the constructor and is associated with the PID
        // Blockchain Verifier thread is started here
        final byte[] buffer = new byte[2048];
        DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
        new Thread(this.blockchainVerifierServer).start();
        while (true){
            try {
                this.socket.receive(packet);
            }
            catch (IOException exception){
                System.out.println("IOE exception caught.");
            }
            String event = new String(packet.getData(), 0, packet.getLength());

            if (event.startsWith("PUBKEYS")){
                MulticastKeys();
            }
            else {
                this.blockchainVerifierServer.AddBlock(event);
            }
        }
    }

    // utility function from Professor Elliott
    public KeyPair GenerateKeyPair() throws Exception {
        KeyPairGenerator keyGenerator = KeyPairGenerator.getInstance("RSA");
        SecureRandom rng = SecureRandom.getInstance("SHA1PRNG", "SUN");
        keyGenerator.initialize(1024, rng);

        return (keyGenerator.generateKeyPair());
    }

    // utility function from Professor Elliott
    // signs data with private key
    public byte[] SignData(byte[] data) throws Exception {
        Signature signer = Signature.getInstance("SHA1withRSA");
        signer.initSign(this.pubPrivKey.getPrivate());
        signer.update(data);
        return (signer.sign());
    }

    // read input from a file, create new block, and multicast it to all other running PIDs
    // many utility functions have veen used from Professor Elliott's website
    public int ReadInputFileMulticast(String filename) {
        int BlockRecordCount = 0;

        try (BufferedReader br = new BufferedReader(new FileReader(filename))) {
            String inputFileLine;
            String[] tokens = new String[10];

            JAXBContext jaxbContext = JAXBContext.newInstance(BlockRecord.class);
            Marshaller jaxbMarshaller = jaxbContext.createMarshaller();

            // CDE Make the output pretty printed:
            jaxbMarshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true);

            while ((inputFileLine = br.readLine()) != null) {
                StringWriter preSW = new StringWriter();
                BlockRecord newBlockRecord = new BlockRecord();
                // split the input line
                tokens = inputFileLine.split(" +");

                // set PID
                newBlockRecord.setAPID("PID: " + this.PID);

                // create new block ID for the line of data
                String blockUUID = UUID.randomUUID().toString();
                newBlockRecord.setABlockID(blockUUID);

                // sign the block ID by current PID processing
                String signedBlockUUID = Base64.getEncoder().encodeToString(this.SignData(blockUUID.getBytes()));
                newBlockRecord.setASignedBlockUUID(signedBlockUUID);

                // timestamp of when data was created
                // utility code from Professor Elliott
                Date date = new Date();
                String timeStamp = String.format("%1$s %2$tF.%2$tT", "", date);
                newBlockRecord.setATimestamp(timeStamp + "." + this.PID);

                // parse tokenized line input string from file
                newBlockRecord.setFFname(tokens[0]);
                newBlockRecord.setFLname(tokens[1]);
                newBlockRecord.setFDOB(tokens[2]);
                newBlockRecord.setFSSNum(tokens[3]);
                newBlockRecord.setGDiag(tokens[4]);
                newBlockRecord.setGTreat(tokens[5]);
                newBlockRecord.setGRx(tokens[6]);

                // patient data is now all in one block
                // following code will package it into XML as the block
                // utility code from Professor Elliott
                jaxbMarshaller.marshal(newBlockRecord, preSW);
                String preXMLstring = preSW.toString();
                MessageDigest md = MessageDigest.getInstance("SHA-256");
                md.update(preXMLstring.getBytes());
                byte byteData[] = md.digest();

                // CDE: Convert the byte[] to hex format. THIS IS NOT VERFIED CODE:
                // utility code from Professor Elliott
                StringBuffer SHAstringBuffer = new StringBuffer();
                for (int i = 0; i < byteData.length; i++) {
                    SHAstringBuffer.append(Integer.toString((byteData[i] & 0xff) + 0x100, 16).substring(1));
                }

                // block will now be signed with private key of the current PID
                // convert string buffer to a string and set in BlockRecord
                String SHA256String = SHAstringBuffer.toString();
                newBlockRecord.setASHA256String(SHA256String);

                // sign SHA256string with private key
                // convert SignedSHA256String to a string and set in BlockRecord
                String SignedSHA256String = Base64.getEncoder().encodeToString(SignData(SHA256String.getBytes()));
                newBlockRecord.setASignedSHA256(SignedSHA256String);

                // entire block is now signed, convert again to XML
                StringWriter postSW = new StringWriter();
                jaxbMarshaller.marshal(newBlockRecord, postSW);
                String postXMLString = postSW.toString();

                MulticastBlock(postXMLString);

                BlockRecordCount+=1;
            }
        } catch (Exception exception) {
            System.out.println("Exception caught. Potentially incorrect filename.\n Please try another filename.");
            return BlockRecordCount;
        }
        return BlockRecordCount;
    }

    // source for UDP multicasting: https://www.baeldung.com/java-broadcast-multicast
    public void MulticastBlock(String XMLblock){
        int[] verfiedBlockchainPorts = {4820, 4821, 4822};

        InetAddress hostname = null;
        try {
            hostname = InetAddress.getLocalHost();
        }
        catch (UnknownHostException exception){
            System.out.println("MulticastBlock: Unknown host.");
        }

        for (int port : verfiedBlockchainPorts) {
            DatagramPacket packet = new DatagramPacket(XMLblock.getBytes(), (XMLblock.getBytes()).length, hostname, port);
            try {
                this.socket.send(packet);
            }
            catch (IOException exception){
                System.out.println("MulticastBlock: Unable to send packet");
            }
        }
    }

    // source for UDP multicasting: https://www.baeldung.com/java-broadcast-multicast
    // multicast the public keys of the servers to the BlockchainPublicKeysServer
    public void MulticastKeys(){
        int[] blockchainPubKeysPort = {4710, 4711, 4712};

        String pubKeyString = "PUBKEY----" + this.PID + "----";
        byte[] publicKey = this.pubPrivKey.getPublic().getEncoded();
        String encodedPublicKey = Base64.getEncoder().encodeToString(publicKey);

        String eventString = pubKeyString + encodedPublicKey;

        InetAddress hostname = null;
        try {
            hostname = InetAddress.getLocalHost();
        }
        catch (UnknownHostException exception){
            System.out.println("MulticastKeys: Unknown host.");
        }

        for (int port : blockchainPubKeysPort) {
            DatagramPacket packet = new DatagramPacket(eventString.getBytes(), eventString.length(), hostname, port);
            try {
                this.socket.send(packet);
            }
            catch (IOException exception){
                System.out.println("MulticastKeys: Unable to send packet");
            }
        }
    }

    // multicast an event to the unverified server
    public void MulticastEvent(String event){
        int[] verfiedBlockchainPorts = {4820, 4821, 4822};

        InetAddress hostname = null;
        try {
            hostname = InetAddress.getLocalHost();
        }
        catch (UnknownHostException exception){
            System.out.println("MulticastEvent: Unknown host.");
        }

        for (int port : verfiedBlockchainPorts) {
            DatagramPacket packet = new DatagramPacket(event.getBytes(), (event.getBytes()).length, hostname, port);

            try {
                this.socket.send(packet);
            }
            catch (IOException exception){
                System.out.println("MulticastEvent: Unable to send packet");
            }
        }
    }
}

class BlockchainServer{

}

public class Blockchain {
    public static void main(String[] args) {
        int PID;
        if (args.length < 1) PID = 0;

        else {
            switch (args[0]){
                case "1":
                    PID = 1;
                    break;
                case "2":
                    PID = 2;
                    break;
                case "3":
                    PID = 3;
                    break;
                default:
                    PID = 0;
            }
        }
        int blockchainPublicKeysServerPort = 4710 + PID;
        int unverifiedBlockchainServerPort = 4820 + PID;
        int verifiedBlockchainServerPort = 4930 + PID;

        System.out.println("Server PIDs: " + blockchainPublicKeysServerPort + " " + unverifiedBlockchainServerPort + " " + verifiedBlockchainServerPort);

        BlockchainPublicKeysServer blockchainPublicKeys = new BlockchainPublicKeysServer(blockchainPublicKeysServerPort, PID);
        BlockchainVerifierServer blockchainVerifier = new BlockchainVerifierServer(PID, blockchainPublicKeys);
        //BlockchainVerifierServer blockchainVerifier = new BlockchainVerifierServer();
        UnverifiedBlockchainServer unverifiedBlockchainServer = new UnverifiedBlockchainServer(unverifiedBlockchainServerPort, blockchainPublicKeys, verifiedBlockchainServerPort, PID);

        Thread keysThread = new Thread(blockchainPublicKeys);
        Thread unverifiedThread = new Thread(unverifiedBlockchainServer);
        Thread verifierThread = new Thread(blockchainVerifier);

        keysThread.start();
        unverifiedThread.start();
        verifierThread.start();

        // loop until all public keys have been collected
        // once all servers have it, continue with console commands
        while (!(blockchainPublicKeys.getHashSize() == 3)){
            System.out.flush();
            break;
        }

        BufferedReader in = new BufferedReader(new InputStreamReader(System.in));
        String userString;

        System.out.println("Console commands:");
        System.out.println("\"C\": Loop through the blockchain and keep a tally of solved blockchains");
        System.out.println("\"R <filename>\": Reads a file to create new data");
        System.out.println("\"V <option>\" - Options: hash, signature, threshold: Verify the blockchain. (Example: V hash || V signature || V threshold)");
        System.out.println("\"L\": List each block in the blockchain.");
        System.out.println("'quit' to end program.");

        try {
            do {
                userString = in.readLine();
                if (userString.equals("C")){

                }
                else if (userString.startsWith("R ")){
                    String[] stringSplit = userString.split(" ");
                    String filename = stringSplit[1];
                    int totalBlockRecords = unverifiedBlockchainServer.ReadInputFileMulticast(filename);
                    System.out.println("BlockRecords added from " + filename + " : " + totalBlockRecords);
                }
                else if(userString.equals("V hash")){

                }
                else if(userString.equals("V signature")){

                }
                else if(userString.equals("V threshold")){

                }
                else if(userString.equals("L")){

                }
                else {
                    System.out.println("Not an option, please choose one of the console commands above.");
                }

            } while (!userString.equals("quit")); {
                System.out.println("User ended program.");
                System.exit(0);
            }
        }
        catch (IOException exception){
            System.out.println("Blockchain Main: IO Exception");
        }
    }
}

@XmlRootElement
class BlockRecord{
    /* Examples of block fields: */
    String SHA256String;
    String SignedSHA256;
    String BlockID;
    String VerificationProcessID;
    String CreatingProcess;
    String PreviousHash;
    String Fname;
    String Lname;
    String SSNum;
    String DOB;
    String Diag;
    String Treat;
    String Rx;
    String Timestamp;
    String PID;
    String signedBlockUUID;

  /* Examples of accessors for the BlockRecord fields. Note that the XML tools sort the fields alphabetically
     by name of accessors, so A=header, F=Indentification, G=Medical: */

    public String getASHA256String() {return SHA256String;}
    @XmlElement
    public void setASHA256String(String SH){this.SHA256String = SH;}

    public String getASignedSHA256() {return SignedSHA256;}
    @XmlElement
    public void setASignedSHA256(String SH){this.SignedSHA256 = SH;}

    public String getACreatingProcess() {return CreatingProcess;}
    @XmlElement
    public void setACreatingProcess(String CP){this.CreatingProcess = CP;}

    public String getAVerificationProcessID() {return VerificationProcessID;}
    @XmlElement
    public void setAVerificationProcessID(String VID){this.VerificationProcessID = VID;}

    public String getABlockID() {return BlockID;}
    @XmlElement
    public void setABlockID(String BID){this.BlockID = BID;}

    public String getFSSNum() {return SSNum;}
    @XmlElement
    public void setFSSNum(String SS){this.SSNum = SS;}

    public String getFFname() {return Fname;}
    @XmlElement
    public void setFFname(String FN){this.Fname = FN;}

    public String getFLname() {return Lname;}
    @XmlElement
    public void setFLname(String LN){this.Lname = LN;}

    public String getFDOB() {return DOB;}
    @XmlElement
    public void setFDOB(String DOB){this.DOB = DOB;}

    public String getGDiag() {return Diag;}
    @XmlElement
    public void setGDiag(String D){this.Diag = D;}

    public String getGTreat() {return Treat;}
    @XmlElement
    public void setGTreat(String D){this.Treat = D;}

    public String getGRx() {return Rx;}
    @XmlElement
    public void setGRx(String D){this.Rx = D;}

    public String getATimestamp() {return Timestamp;}
    @XmlElement
    public void setATimestamp(String timestamp){this.Timestamp = timestamp;}

    public String getAPID() {return PID;}
    @XmlElement
    public void setAPID(String PID){this.PID = PID;}

    public String getASignedBlockUUID() {return signedBlockUUID;}
    @XmlElement
    public void setASignedBlockUUID(String signedBlockUUID){this.signedBlockUUID = signedBlockUUID;}
}