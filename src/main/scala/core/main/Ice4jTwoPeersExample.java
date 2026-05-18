package core.main;

import org.ice4j.Transport;
import org.ice4j.TransportAddress;
import org.ice4j.ice.*;
import org.ice4j.ice.harvest.CandidateHarvesterSet;
import org.ice4j.ice.harvest.StunCandidateHarvester;
import org.ice4j.ice.harvest.UPNPHarvester;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.DatagramPacket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;


class Ice4jTwoPeersExample {

    static class ParamReader {
        BufferedReader input = new BufferedReader(new InputStreamReader(System.in));
        String read(String name) throws IOException {
            System.out.print(name + ": ");
            return input.readLine();
        }
    }

    static class Peer implements Runnable {
        final String name = "Peer";

        Agent agent;
        IceMediaStream stream;
        Component component;

        @Override
        public void run() {
            try {
                createAgent();
                printMe();
                setRemote();
                startConnectivityEstablishment();

                waitForConnected();
                sendMessage();
                receiveMessage();

                waitForTerminate();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        void createAgent() throws Exception {
            agent = new Agent();

            var stunAddress = new TransportAddress("stun.l.google.com", 19302, Transport.UDP);
            agent.addCandidateHarvester(new StunCandidateHarvester(stunAddress));
            agent.addCandidateHarvester(new UPNPHarvester());

            stream = agent.createMediaStream("data");
            component = agent.createComponent(stream, 25000, 25000, 26000);
        }

        void printMe() {
            System.out.println("Ufrag: "    + agent.getLocalUfrag());
            System.out.println("Password: " + agent.getLocalPassword());
            for (var candidate : component.getLocalCandidates()) {
                var address = candidate.getTransportAddress();
                System.out.println("Ip: "       + address.getHostAddress());
                System.out.println("Port: "     + address.getPort());
            }
        }

        void setRemote() throws IOException {
            var reader = new ParamReader();
            
            stream.setRemoteUfrag(reader.read("Ufrag"));
            stream.setRemotePassword(reader.read("Password"));
            
            var address = new TransportAddress(reader.read("Ip"), Integer.parseInt(reader.read("Port")), Transport.UDP);
            RemoteCandidate rc = new RemoteCandidate(
                address,
                component,
                CandidateType.STUN_CANDIDATE,
                address.toString(),
                123,
                null);
            component.addRemoteCandidate(rc);
        }

        void startConnectivityEstablishment() {
            System.out.println(name + " starting ICE");
            agent.startConnectivityEstablishment();
        }

        void waitForConnected() throws Exception {
            while (agent.getState() != IceProcessingState.COMPLETED) {
                if (agent.getState() == IceProcessingState.FAILED)
                    throw new RuntimeException(name + " ICE failed");
                Thread.sleep(100);
            }
            System.out.println(name + " ICE connected");
        }

        void sendMessage() throws Exception {
            String text = "hello from " + name;
            byte[] data = text.getBytes(StandardCharsets.UTF_8);
            CandidatePair pair = component.getSelectedPair();
            DatagramPacket packet = new DatagramPacket(data, data.length, pair.getRemoteCandidate().getTransportAddress());
            pair.getIceSocketWrapper().getUDPSocket().send(packet);

            System.out.println(name + " sent: " + text);
        }

        void receiveMessage() throws Exception {
            byte[] buf = new byte[1024];
            DatagramPacket packet = new DatagramPacket(buf, buf.length);

            var socket = component.getSelectedPair().getIceSocketWrapper().getUDPSocket();
            socket.receive(packet);
            socket.close();

            String msg = new String(packet.getData(), 0, packet.getLength(), StandardCharsets.UTF_8);
            System.out.println(name + " received: " + msg);
        }

        void waitForTerminate() throws InterruptedException {
            while (agent.getState() != IceProcessingState.TERMINATED)
                Thread.sleep(100);
            agent.free();
        }
    }

    static void main(String[] args) throws Exception {
        new Peer().run();

        var field = CandidateHarvesterSet.class.getDeclaredField("threadPool");
        field.setAccessible(true);
        ((ExecutorService)field.get(null)).close();
        
        System.out.println("done");
    }
}
