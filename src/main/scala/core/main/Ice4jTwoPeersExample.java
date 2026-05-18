package core.main;

import org.ice4j.Transport;
import org.ice4j.TransportAddress;
import org.ice4j.ice.*;
import org.ice4j.ice.harvest.CandidateHarvesterSet;
import org.ice4j.ice.harvest.StunCandidateHarvester;
import org.ice4j.ice.harvest.UPNPHarvester;

import java.net.DatagramPacket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;


class Ice4jTwoPeersExample {

    static class Peer implements Runnable {
        final String name;
        final CountDownLatch gatherDone;
        final CountDownLatch peerDone = new CountDownLatch(1);

        Agent agent;
        IceMediaStream stream;
        Component component;

        Peer(String name, CountDownLatch gatherDone) {
            this.name = name;
            this.gatherDone = gatherDone;
        }

        @Override
        public void run() {
            try {
                createAgent();
                gatherDone.countDown();

                peerDone.await();
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

            StringBuilder localCandidatesStr = new StringBuilder();
            localCandidatesStr.append(name).append(" local candidates:");
            for (LocalCandidate c : component.getLocalCandidates())
                localCandidatesStr.append("\n  ").append(c.getTransportAddress());
            System.out.println(localCandidatesStr);
        }

        void setRemote(Peer other) {
            stream.setRemoteUfrag(other.agent.getLocalUfrag());
            stream.setRemotePassword(other.agent.getLocalPassword());

            for (LocalCandidate c : other.component.getLocalCandidates()) {
                RemoteCandidate rc = new RemoteCandidate(
                    c.getTransportAddress(),
                    component,
                    c.getType(),
                    c.getFoundation(),
                    c.getPriority(),
                    null);
                component.addRemoteCandidate(rc);
            }

            peerDone.countDown();
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
        CountDownLatch gatherDone = new CountDownLatch(2);

        Peer a = new Peer("A", gatherDone);
        Peer b = new Peer("B", gatherDone);

        Thread ta = new Thread(a);
        Thread tb = new Thread(b);

        ta.start();
        tb.start();

        gatherDone.await();

        a.setRemote(b);
        b.setRemote(a);

        ta.join();
        tb.join();

        var field = CandidateHarvesterSet.class.getDeclaredField("threadPool");
        field.setAccessible(true);
        ((ExecutorService)field.get(null)).close();
        
        System.out.println("done");
    }
}
