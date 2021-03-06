/* CDE: The JAXB libraries and utilities*/

import com.sun.security.ntlm.Server;
import org.omg.PortableServer.POA;
import sun.awt.image.ImageWatched;

import javax.xml.bind.*;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.crypto.Data;
import java.io.*;
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

/**
 * Class: CSC 435 - Blockchain Server
 * Author: Raquib Talukder
 * Sources: http://condor.depaul.edu/elliott/se435/hw/programs/Blockchain/program-block.html
 *          http://condor.depaul.edu/elliott/435/hw/programs/Blockchain/BlockInputE-output.txt
 *          http://condor.depaul.edu/elliott/435/hw/programs/Blockchain/BlockInputE.java
 *          http://condor.depaul.edu/elliott/435/hw/programs/Blockchain/BlockI.java
 *          http://condor.depaul.edu/elliott/435/hw/programs/Blockchain/WorkB.java
 *          https://www.baeldung.com/udp-in-java
 *          https://www.baeldung.com/java-broadcast-multicast
 *
 **/

// class to keep track of the public keys of the different PIDs of the severs
class BlockchainPublicKeysServer implements Runnable {
    DatagramSocket socket;
    int PID;
    // key = server PID
    // value = public key for that server PID
    HashMap<String,PublicKey> PIDPubKeys;

    public BlockchainPublicKeysServer(int blockchainPublicKeysPort, int PID){
        this.PID = PID;
        this.PIDPubKeys = new HashMap<>();
        // socket created in order to receive public keys from the different servers
        try {
            DatagramSocket datagramSocket = new DatagramSocket(blockchainPublicKeysPort);
            this.socket = datagramSocket;
        }
        catch (Exception exception){
            System.out.println("BlockchainPubKeysServer: Exception caught");
        }
    }

    // function used so that the main thread doesn't start until all 3 keys from the servers have been received
    public int GetHashSize(){
        return this.PIDPubKeys.size();
    }

    // function used to return the public key depending on the
    public PublicKey GetPubKey(String PID){
        return this.PIDPubKeys.get(PID);
    }

    @Override
    public void run() {
        // source for UDP Datagram packets: https://www.baeldung.com/udp-in-java
        // DatagramPacket is used to receive messages on speficied port
        // DatagramSocket is created in the constructor and is associated with the PID
        final byte[] buffer = new byte[2048];
        DatagramPacket packet = new DatagramPacket(buffer, buffer.length);

        // public key server thread running to receive keys from servers when they're broadcasted
        while (true) {
            try {
                this.socket.receive(packet);
            } catch (IOException exception) {
                System.out.println("IOE exception caught.");
            }

            // packet received from a multicast - break it apart to get the data
            String pubKeyPacket = new String(packet.getData(), 0, packet.getLength());
            // key is received in following format: PUBKEY----ServerPID----PublicKey
            // split in order to isolated the public key
            String[] splitString = pubKeyPacket.split("----");

            try {
                // utility code from Professor Elliott
                // the public key is received in a byte stream
                // this utility code is used to convert the data into a useable public key and server PID
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

// server that is used to read in input files, turn them into block records, and multicast them out to the verifier servers
class UnverifiedBlockchainServer extends Thread {
    BlockchainVerifierServer blockchainVerifierServer;
    DatagramSocket socket;
    KeyPair pubPrivKey;
    int PID;

    public UnverifiedBlockchainServer(int unverifiedBlockchainServerPort, BlockchainPublicKeysServer blockchainPublicKeysServer, int PID) {
        this.PID = PID;


        try {
            // generate public and private key to use to sign data and also verify signatures
            this.pubPrivKey = GenerateKeyPair();
            // socket used to multicast unverified blocks and when to broadcast the public keys
            DatagramSocket datagramSocket = new DatagramSocket(unverifiedBlockchainServerPort);
            this.socket = datagramSocket;
            this.blockchainVerifierServer = new BlockchainVerifierServer(blockchainPublicKeysServer, this.PID, this.pubPrivKey, this.socket);
        }
        catch (Exception exception){
            System.out.println("UnverifiedBlockchainServer: Exception caught");
        }
        // once PID server 2 is started - multicast public keys to all listening public key servers
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
        // server can either receive an event to multicast out public keys OR unverified blocks to send to the verifier server
        while (true){
            try {
                // read packet in
                this.socket.receive(packet);
            }
            catch (IOException exception){
                System.out.println("IOE exception caught.");
            }
            String event = new String(packet.getData(), 0, packet.getLength());

            // event to multicast keys out
            if (event.startsWith("PUBKEYS")){
                MulticastKeys();
            }
            else {
                // unverified block receive
                // add it to the verifier server in order to begin the verification process
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

            // utility code from Professor Elliott
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

                // multicast unverified block to all other open sockets
                MulticastBlock(postXMLString);

                // increment how many records read by 1
                BlockRecordCount+=1;
            }
        } catch (Exception exception) {
            System.out.println("Exception caught. Potentially incorrect filename.\n Please try another filename.");
            return BlockRecordCount;
        }
        // return how many lines were read in
        return BlockRecordCount;
    }

    // source for UDP multicasting: https://www.baeldung.com/java-broadcast-multicast
    public void MulticastBlock(String XMLblock){
        int[] verfiedBlockchainPorts = {4820, 4821, 4822};

        // the server currently only runs on local host
        InetAddress hostname = null;
        try {
            hostname = InetAddress.getLocalHost();
        }
        catch (UnknownHostException exception){
            System.out.println("MulticastBlock: Unknown host.");
        }

        // for all the hardcoded public key servers
        // transmit the unverifed blocks to the unverified server ports
        // they will notice that the block is unverified and pass it on to the verifier server
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
    // multicast the public keys of the servers listening BlockchainPublicKeysServer
    public void MulticastKeys(){
        int[] blockchainPubKeysPort = {4710, 4711, 4712};

        // add a header that's easy to take apart
        String pubKeyString = "PUBKEY----" + this.PID + "----";
        //
        byte[] publicKey = this.pubPrivKey.getPublic().getEncoded();
        String encodedPublicKey = Base64.getEncoder().encodeToString(publicKey);

        // string that will be multicasted out
        String eventString = pubKeyString + encodedPublicKey;

        // the server currently only runs on local host
        InetAddress hostname = null;
        try {
            hostname = InetAddress.getLocalHost();
        }
        catch (UnknownHostException exception){
            System.out.println("MulticastKeys: Unknown host.");
        }

        // for all the hardcoded public key server ports
        // transmit the public key for the particular server PID running
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
    // used to multicast events - in particular when to execute the MulticastKeys function
    public void MulticastEvent(String event){
        int[] verfiedBlockchainPorts = {4820, 4821, 4822};

        // the server currently only runs on local host
        InetAddress hostname = null;
        try {
            hostname = InetAddress.getLocalHost();
        }
        catch (UnknownHostException exception){
            System.out.println("MulticastEvent: Unknown host.");
        }

        // for all the hardcoded unverifed servers
        // transmit the unverifed blocks to the unverified server ports
        // the server will know to next multicast the keys
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

// server to verify unverifed blocks broadcasted by files read
class BlockchainVerifierServer extends Thread {
    int PID;
    KeyPair pubPrivKey;
    BlockchainPublicKeysServer blockchainPublicKeysServer;
    DatagramSocket socket;
    final BlockingQueue<BlockRecord> unverifiedBlocksList;
    LinkedList<BlockRecord> blockchainList;


    public BlockchainVerifierServer(BlockchainPublicKeysServer blockchainPublicKeysServer, int PID, KeyPair pubPrivKey, DatagramSocket socket){
        this.PID = PID;
        this.socket = socket;
        this.pubPrivKey = pubPrivKey;
        this.blockchainPublicKeysServer = blockchainPublicKeysServer;
        this.unverifiedBlocksList = new LinkedBlockingQueue<>();
        this.blockchainList = new LinkedList<>();
        // dummy data for the first block in blockchain list
        // this is used to we can add new blocks to the chain which utilizes the previous SHA256 hash
        BlockRecord dummyRecord = new BlockRecord();
        dummyRecord.setABlockID("236e1834-5a02-46a3-974e-58e90bf87d2b");
        dummyRecord.setASHA256String("77df263f49123356d28a4a8715d25bf5b980beeeb503cab46ea61ac9f3320eda");
        dummyRecord.setABlockIndex("0");
        this.blockchainList.add(dummyRecord);

    }

    // function to add a block to be later verified
    public void AddBlock(String XMLblock){
        // function to turn XML blocks received from the socket into a BlockRecord
        BlockRecord convertedBlock = this.ConvertBlock(XMLblock);
        try {
            // sometimes unreadable blocks will be returned a 'null'
            // all other added to the unverified list to be verified by the main running thread
            if (convertedBlock != null) {
                this.unverifiedBlocksList.add(convertedBlock);
            }
        } catch (Exception exception){
            exception.printStackTrace();
            System.out.println("AddBlock: Exception thrown");
        }
    }

    // function to take XML block and convert it into a BlockRecord object to be verified
    public BlockRecord ConvertBlock(String XMLblock){
        BlockRecord convertedBlock = null;
        try {
            // utility code from Professor Elliott
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
    // verifies the signature that unverified server put on the block
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

    // utility function from Professor Elliott
    // signs data with private key
    public byte[] SignData(byte[] data) throws Exception {
        Signature signer = Signature.getInstance("SHA1withRSA");
        signer.initSign(this.pubPrivKey.getPrivate());
        signer.update(data);
        return (signer.sign());
    }

    // main thread that's running in order to verify blocks that have been added to the unverified block list
    @Override
    public void run(){
        while (true) {
            if(!(unverifiedBlocksList.isEmpty())){
                BlockRecord workingBlock = unverifiedBlocksList.remove();

                // utility code from Professor Elliott
                // find out who signed the block
                byte[] decodedBlockSignature = Base64.getDecoder().decode(workingBlock.getASignedBlockUUID());
                String[] splitPID = workingBlock.getAPID().split(" ");
                PublicKey getPIDPubKey = this.blockchainPublicKeysServer.GetPubKey(splitPID[1]);
                boolean verifiedSignatureBlockID = this.VerifySignature(workingBlock.getABlockID().getBytes(), getPIDPubKey, decodedBlockSignature);

                // if verified - continute
                // if not verified - break from loop and begin checking another block
                if(!verifiedSignatureBlockID){
                    continue;
                }

                // get last block index in block chain in order to update workingBlocks block index
                // this is used to keep track of what order the block chain is in
                BlockRecord lastBCrecord = this.blockchainList.getLast();
                Integer lastBlockIndex = Integer.valueOf(lastBCrecord.getABlockIndex());
                int workingBlockIndex = (lastBlockIndex + 1);
                workingBlock.setABlockIndex(String.valueOf(workingBlockIndex));

                String lastBlockSHA256 = lastBCrecord.getASHA256String();
                String workingBlockID = workingBlock.getABlockID();

                try {
                    // utility code from Professor Elliott
                    JAXBContext jaxbContext = JAXBContext.newInstance(BlockRecord.class);
                    Marshaller jaxbMarshaller = jaxbContext.createMarshaller();
                    // CDE Make the output pretty printed:
                    jaxbMarshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true);

                    // create random seed to set for the current block being verified
                    Random randomBytes = new Random();
                    byte[] randomSeedBytes = new byte[256];

                    // this loop is where work is being done
                    while (true){
                        StringWriter preSW = new StringWriter();
                        // random bytes added to byte array
                        randomBytes.nextBytes(randomSeedBytes);

                        // utility code from Professor Elliott
                        jaxbMarshaller.marshal(workingBlock, preSW);
                        String workingBlockXML = preSW.toString();

                        MessageDigest md = MessageDigest.getInstance("SHA-256");
                        // current XML data + previous SHA256 string
                        md.update((workingBlockXML + lastBlockSHA256).getBytes());

                        // utility code from Professor Elliott
                        // CDE: Convert the byte[] to hex format. THIS IS NOT VERFIED CODE:
                        // utility code from Professor Elliott
                        byte[] byteData = md.digest();

                        // utility code from Professor Elliott
                        String stringOut = DatatypeConverter.printHexBinary(randomSeedBytes); // Turn into a string of hex values
                        System.out.println("Hash is: " + stringOut);
                        int work = Integer.parseInt(stringOut.substring(0,4),16); // Between 0000 (0) and FFFF (65535)
                        System.out.println("First 16 bits in Hex and Decimal: " + stringOut.substring(0,4) +" and " + work);

                        // check if hash is < 20480 - if not create new random seed
                        if (!(work < 20480)){
                            System.out.println("not solved");
                        }
                        // work completed is < 2048
                        if (work < 20480){
                            System.out.println("solved");
                            // CDE: Convert the byte[] to hex format. THIS IS NOT VERFIED CODE:
                            // utility code from Professor Elliott
                            StringBuffer SHAstringBuffer = new StringBuffer();
                            for (int i = 0; i < byteData.length; i++) {
                                SHAstringBuffer.append(Integer.toString((byteData[i] & 0xff) + 0x100, 16).substring(1));
                            }

                            // block will now be signed with private key of the current PID
                            // convert string buffer to a string and set in BlockRecord
                            String SHA256String = SHAstringBuffer.toString();
                            String SignedSHA256String = Base64.getEncoder().encodeToString(SignData(SHA256String.getBytes()));
                            workingBlock.setASHA256String(SHA256String);
                            workingBlock.setASignedSHA256(SignedSHA256String);
                            workingBlock.setAVerificationProcessID(String.valueOf(this.PID));

                            // timestamp of when data was created
                            // utility code from Professor Elliott
                            Date date = new Date();
                            String timeStamp = String.format("%1$s %2$tF.%2$tT", "", date);
                            workingBlock.setATimestamp(timeStamp + "." + this.PID);

                            // add verified block to the list
                            this.blockchainList.add(workingBlock);

                            // create a ledger to be multicast to listening blockchain servers
                            byte[] blockChainBytes = CreateBlockLedgerRecord();

                            // when a new block is added to the ledger - it's multicast to listening blockchain servers
                            MulticastVerifiedBlock(blockChainBytes);

                            // break out of loop since block has been verified
                            break;
                        }
                    }
                }
                catch (Exception exception) {
                    exception.printStackTrace();
                    System.out.println("BlockchainVerifierServer: Running thread exception thrown");
                }
            }
        }
    }

    // source for UDP multicasting: https://www.baeldung.com/java-broadcast-multicast
    // function used to multicast that a block has been verified
    public void MulticastVerifiedBlock(byte[] XMLblockBytes){
        int[] verfiedBlockchainPorts = {4930, 4931, 4932};

        // currently only runs localhost
        InetAddress hostname = null;
        try {
            hostname = InetAddress.getLocalHost();
        }
        catch (UnknownHostException exception){
            System.out.println("MulticastVerifiedBlock: Unknown host.");
        }

        for (int port : verfiedBlockchainPorts) {
            DatagramPacket packet = new DatagramPacket(XMLblockBytes, XMLblockBytes.length, hostname, port);
            try {
                this.socket.send(packet);
            }
            catch (IOException exception){
                System.out.println("MulticastVerifiedBlock: Unable to send packet");
            }
        }
    }

    // function to create the blockchain ledger in XML to be multicast to listenign blockchain servers
    public byte[] CreateBlockLedgerRecord(){
        try {
            // utility code from Professor Elliott
            JAXBContext jaxbContext = JAXBContext.newInstance(BlockRecord.class);
            Marshaller jaxbMarshaller = jaxbContext.createMarshaller();
            // CDE Make the output pretty printed:
            jaxbMarshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true);
            StringWriter sw = new StringWriter();

            for (int i = 0; i < this.blockchainList.size(); i++) {
                jaxbMarshaller.marshal(this.blockchainList.get(i), sw);
            }

            // utility code from Professor Elliott
            String fullBlock = sw.toString();
            String XMLHeader = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>";
            String cleanBlock = fullBlock.replace(XMLHeader, "");
            // Show the string of concatenated, individual XML blocks:
            String XMLBlock = XMLHeader + "\n<BlockLedger>" + cleanBlock + "</BlockLedger>";

            return XMLBlock.getBytes();
        }
        catch (Exception exception) {
            System.out.println("CreateBlockLedgerRecord: Exception thrown");
            return null;
        }
    }
}

// server listening to updated blockchain multicast from the verifier server
// has a global block chain list that's compared to with new blockchains received
class BlockchainServer implements Runnable {
    int blockchainServerPort;
    int PID;
    DatagramSocket socket;
    LinkedList<BlockRecord> globalBlockChainList;

    public BlockchainServer(int blockchainServerPort, int PID){
        this.PID = PID;
        this.blockchainServerPort = blockchainServerPort;
        this.globalBlockChainList = new LinkedList<>();

        try {
            DatagramSocket datagramSocket = new DatagramSocket(this.blockchainServerPort);
            this.socket = datagramSocket;
        }
        catch (Exception exception){
            System.out.println("UnverifiedBlockchainServer: Exception caught");
        }
    }

    @Override
    public void run() {
        final byte[] buffer = new byte[20000];
        DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
        // main thread that is listening to new block chains being broadcasted
        while(true){
            try {
                this.socket.receive(packet);
            }
            catch (IOException exception){
                System.out.println("IOE exception caught.");
            }
            // once a new block chain has been received - pass it to the update block chain function
            String newBlockChainXML = new String(packet.getData(), 0, packet.getLength());
            UpdateBlockChain(newBlockChainXML);

            // PID 0 is used to keep track of the updated ledgers
            // new ledgers overwrite old ones
            if (this.PID == 0){
                WriteBlockchainLedger(newBlockChainXML);
            }
        }
    }

    public void UpdateBlockChain(String newBlockChainXML){
        LinkedList<BlockRecord> tempBlockchainList = new LinkedList<>();

        // utility code from Professor Elliott
        // remove the XML header along with <BlockLedger> and </BlockLedger>
        String XMLHeader = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>";
        String blockLedgerXMLHeaderRemoved = newBlockChainXML.replace(XMLHeader, "");
        String openBlockLedgerRemoved = blockLedgerXMLHeaderRemoved.replace("<BlockLedger>", "");
        String cleanedBlockRecords = openBlockLedgerRemoved.replace("</BlockLedger>", "");

        // contains string versions of the BlockRecords
        String[] splitBlockRecords = cleanedBlockRecords.split("\n\n");
        String[] removeFirstIndexBlockRecords = Arrays.copyOfRange(splitBlockRecords, 1, splitBlockRecords.length);

        // go through string BlockVersions and convert them to actual BlockRecord objects
        for (String block : removeFirstIndexBlockRecords){
            BlockRecord convertedBlock = ConvertBlock(block);
            if (convertedBlock != null){
                tempBlockchainList.add(convertedBlock);
            }
        }

        // if the current global list and new list are the same size
        // check and see if they're different at any point
        // if they're the same then no action is done
        if (this.globalBlockChainList.size() == tempBlockchainList.size()){
            for (int i =1; i < this.globalBlockChainList.size(); i++){
                // if they are different -  i stick with the global
                if(!this.globalBlockChainList.get(i).equals(tempBlockchainList.get(i))){
                    break;
                }
            }
        }
        // if current global list is short than the new one
        // i am assuming that it's more up-to-date and set it as the new global list
        // probably not the best way to do this
        else if (this.globalBlockChainList.size() < tempBlockchainList.size()){
            this.globalBlockChainList = tempBlockchainList;
        }

    }

    // function that's used to convert XML string versions of BlockRecords into actual BlockRecord objects
    public BlockRecord ConvertBlock(String XMLblock){
        BlockRecord convertedBlock = null;
        try {
            StringReader reader = new StringReader(XMLblock);
            JAXBContext jaxbContext = JAXBContext.newInstance(BlockRecord.class);
            Unmarshaller jaxbUnmarshaller = jaxbContext.createUnmarshaller();
            convertedBlock = (BlockRecord) jaxbUnmarshaller.unmarshal(reader);
        }
        catch (Exception exception){
            exception.printStackTrace();
            System.out.println("ConvertBlock (BlockchainServer): Exception caught.");
        }
        return convertedBlock;
    }

    // function that's used by PID 0 in order to write updated BlockchainLedgers
    public void WriteBlockchainLedger(String XMLblock){
        // opening file for XML block logging
        try{
            FileWriter writer = new FileWriter("BlockchainLedger.xml", false);

            // logging XML blockchains
            writer.write(XMLblock);
            writer.close();
        }
        catch (IOException exception){
            System.out.println("WriteBlockchainLedger: Exception caught");
        }

    }
}

// main class that will deal with PIDs
public class Blockchain {
    public static void main(String[] args) {
        int PID;

        // if no arguments given - default is 0
        if (args.length < 1) PID = 0;

        // only PIDs 0,1,2 are accepted
        // all others will be 0, by default
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
        int blockchainServerPort = 4930 + PID;

        System.out.println("Server PIDs: " + blockchainPublicKeysServerPort + " " + unverifiedBlockchainServerPort + " " + blockchainServerPort);

        // getting all the servers ready
        BlockchainPublicKeysServer blockchainPublicKeys = new BlockchainPublicKeysServer(blockchainPublicKeysServerPort, PID);
        UnverifiedBlockchainServer unverifiedBlockchainServer = new UnverifiedBlockchainServer(unverifiedBlockchainServerPort, blockchainPublicKeys, PID);
        BlockchainServer blockChainServer = new BlockchainServer(blockchainServerPort, PID);

        // starting the threads for the servers to listen
        Thread keysThread = new Thread(blockchainPublicKeys);
        Thread unverifiedThread = new Thread(unverifiedBlockchainServer);
        Thread blockChainThread = new Thread(blockChainServer);


        keysThread.start();
        unverifiedThread.start();
        blockChainThread.start();

        // loop until all public keys have been collected
        // once all servers have it, continue with console commands
        while (!(blockchainPublicKeys.GetHashSize() == 3)){
            System.out.flush();
            break;
        }

        // to take in user strings
        BufferedReader in = new BufferedReader(new InputStreamReader(System.in));
        String userString;

        // only reading in files has been implmented
        System.out.println("Console commands:");
        System.out.println("\"R <filename>\": Reads a file to create new data");
        System.out.println("'quit' to end program.");

        try {
            do {
                // read in user commands
                userString = in.readLine();
                // only read input file command implmented
                if (userString.startsWith("R ")){
                    String[] stringSplit = userString.split(" ");
                    String filename = stringSplit[1];
                    int totalBlockRecords = unverifiedBlockchainServer.ReadInputFileMulticast(filename);
                    System.out.println("BlockRecords added from " + filename + " : " + totalBlockRecords);
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

// utility code from Professor Elliott
@XmlRootElement
class BlockRecord{
    /* Examples of block fields: */
    String SHA256String;
    String SignedSHA256;
    String BlockID;
    String BlockIndex;
    String VerificationProcessID;
    String CreatingProcess;
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

    public String getABlockIndex() {return BlockIndex;}
    @XmlElement
    public void setABlockIndex(String BlockIndex){this.BlockIndex = BlockIndex;}
}