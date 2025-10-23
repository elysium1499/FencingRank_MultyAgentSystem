import java.util.*;
import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.CyclicBehaviour;
import jade.domain.DFService;
import jade.domain.FIPAException;
import jade.domain.FIPAAgentManagement.*;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;

public class Referee extends Agent {
    public String name;

    public int pool;
    public List<String> fencers = new ArrayList<>();
    private List<String[]> bouts = new ArrayList<>();
    
    //auxiliary variable 
    private Map<String, String[]> currentBoutAbilities = new HashMap<>();
    private String[] currentBout = null; //contain actual bout to compute
    private Boolean direct = false;


    protected void setup() {
        Object[] args = getArguments();
        if (args != null && args.length > 0) {
            name = (String) args[0];

            // Registrazione nel DF
            DFAgentDescription dfd = new DFAgentDescription();
            ServiceDescription sd = new ServiceDescription();
            dfd.setName(getAID());
            sd.setType("referee");
            sd.setName("referee-service");
            dfd.addServices(sd);

            try {
                DFService.register(this, dfd);
            } catch (FIPAException fe) {
                fe.printStackTrace();
            }
        }
        addBehaviour(new Partecipation());
        addBehaviour(new RecoverBoutInformation());
        addBehaviour(new BoutWinner());
        addBehaviour(new Termination());
    }

    private class Partecipation extends CyclicBehaviour {
        // Say at organizer that referee is available or not
        public void action() {
            ACLMessage msg = receive(MessageTemplate.MatchPerformative(ACLMessage.REQUEST));
            if (msg != null) {
                ACLMessage reply = msg.createReply();
                if (new Random().nextDouble() <= 0.7) {
                    reply.setPerformative(ACLMessage.AGREE);
                    reply.setContent(name);
                } else {
                    reply.setPerformative(ACLMessage.REFUSE);
                }
                send(reply);
            } else {
                block();
            }
        }
    }

    private class RecoverBoutInformation extends CyclicBehaviour {
        // Manage INFORM coming from organizer -> message of pool and fencers in pool
        public void action() {
            MessageTemplate mt = MessageTemplate.and(
                MessageTemplate.and(
                    MessageTemplate.MatchPerformative(ACLMessage.INFORM),
                    MessageTemplate.MatchConversationId("PoolComposition")
                ),
                MessageTemplate.MatchSender(new jade.core.AID("organizer", jade.core.AID.ISLOCALNAME))
            );

            ACLMessage msg = receive(mt);
            if (msg != null) {
                String[] parts = msg.getContent().split(","); //[poolNumber, fencer1, fencer2, ...]
                pool = Integer.parseInt(parts[0]);
                fencers.addAll(Arrays.asList(parts).subList(1, parts.length));
                Collections.sort(fencers);

                createBouts();

                if (!bouts.isEmpty()) {
                    currentBout = bouts.remove(0);
                    currentBoutAbilities.clear();
                    sendBoutMessage(currentBout[0]);  //START compute bout with ability of firt player
                }
            } else {
                block();
            }
        }
    }

    private class BoutWinner extends CyclicBehaviour {
        private final MessageTemplate abilityMT = MessageTemplate.and(
            MessageTemplate.MatchPerformative(ACLMessage.INFORM),
            MessageTemplate.MatchConversationId("ability")
        );
        
        private final MessageTemplate directBoutMT = MessageTemplate.and(
            MessageTemplate.MatchPerformative(ACLMessage.INFORM),
            MessageTemplate.MatchConversationId("directBout")
        );

        public void action() {
            ACLMessage msg = receive(MessageTemplate.or(abilityMT, directBoutMT));
            if (msg != null) {
                String conversationId = msg.getConversationId();

                if ("ability".equals(conversationId)) {
                    String msgSenderName = msg.getSender().getLocalName(); //fencerAID who send ability
                    String[] parts = msg.getContent().split(",");
                    currentBoutAbilities.put(msgSenderName, parts);

                    if (currentBoutAbilities.size() == 1) { //ask the second fencer ability
                        sendBoutMessage(currentBout[1]);

                    } else if (currentBoutAbilities.size() == 2) {
                        String mex = calculateBoutWinner();

                        if (!direct) {
                            ACLMessage resultMsg = new ACLMessage(ACLMessage.INFORM);
                            resultMsg.addReceiver(new AID("organizer", AID.ISLOCALNAME));
                            resultMsg.setConversationId("resultBout");
                            resultMsg.setContent(pool + "," + mex);
                            send(resultMsg);
                        } else {
                            System.out.println("Direct : "+ mex);

                            ACLMessage resultMsgDirect = new ACLMessage(ACLMessage.INFORM);
                            resultMsgDirect.addReceiver(new AID("organizer", AID.ISLOCALNAME));
                            resultMsgDirect.setConversationId("resultDirectBout");
                            resultMsgDirect.setContent(mex);
                            send(resultMsgDirect);
                        }

                        currentBout = null;
                        currentBoutAbilities.clear();

                        if (!bouts.isEmpty()) {
                            currentBout = bouts.remove(0);
                            sendBoutMessage(currentBout[0]);
                        } else {
                            ACLMessage endMex = new ACLMessage(ACLMessage.INFORM);
                            endMex.addReceiver(new AID("organizer", AID.ISLOCALNAME));
                            endMex.setConversationId("poolEnd");
                            endMex.setContent("finish");
                            send(endMex);
                        }
                    }
                } else if ("directBout".equals(conversationId)) {
                    String[] parts = msg.getContent().split(",");   //fencerAID1,fencerAID2
                    currentBout = new String[]{parts[0], parts[1]};
                    direct = true;
                    
                    sendBoutMessage(currentBout[0]);
                }
            } else {
                block();
            }
        }
    }

    private class Termination extends CyclicBehaviour {
        private final MessageTemplate mt = MessageTemplate.MatchConversationId("discharged");
        
        public void action() {
            ACLMessage msg = receive(mt);
            if (msg != null) {
                if ("CLOSED".equals(msg.getContent())) {
                    try { DFService.deregister(myAgent); } catch (FIPAException e) { e.printStackTrace(); }
                    doDelete();
                }
            } else {
                block();
            }
        }
    }

    private void createBouts() {
        bouts.clear();
        for (int i = 0; i < fencers.size(); i++) {
            for (int j = i + 1; j < fencers.size(); j++) {
                bouts.add(new String[]{fencers.get(i), fencers.get(j)});
            }
        }
    }

    private void sendBoutMessage(String fencer) {
        ACLMessage msg = new ACLMessage(ACLMessage.INFORM);
        msg.addReceiver(new jade.core.AID(fencer, jade.core.AID.ISLOCALNAME));
        msg.setConversationId("bout");
        send(msg);
    }

    private String calculateBoutWinner() {
        // we dont have both the fencer ability
        if (currentBoutAbilities.size() != 2) {
            throw new IllegalArgumentException("Match need two fencer");
        }

        List<Map.Entry<String, String[]>> entries = new ArrayList<>(currentBoutAbilities.entrySet());
        String fencer1 = entries.get(0).getKey();
        String fencer2 = entries.get(1).getKey();

        String[] data1 = entries.get(0).getValue();
        String[] data2 = entries.get(1).getValue();

        // Parsing valori numerici
        int experience1 = Integer.parseInt(data1[1]);
        int experience2 = Integer.parseInt(data2[1]);

        int speed1 = Integer.parseInt(data1[2]);
        int speed2 = Integer.parseInt(data2[2]);

        int parry1 = Integer.parseInt(data1[3]);
        int parry2 = Integer.parseInt(data2[3]);

        int stopThrust1 = Integer.parseInt(data1[4]);
        int stopThrust2 = Integer.parseInt(data2[4]);

        int feint1 = Integer.parseInt(data1[5]);
        int feint2 = Integer.parseInt(data2[5]);

        int emotional1 = Integer.parseInt(data1[6]);
        int emotional2 = Integer.parseInt(data2[6]);

        // Ability valutation (parry, stopThrust, feint → con logica "sasso carta forbice")
        int sumFenc1 = 0, sumFenc2 = 0;
        if (parry1 > stopThrust2 || parry1 == stopThrust2) { sumFenc1 += 1; sumFenc2 -= 1; } else { sumFenc1 -= 1; sumFenc2 += 1; }
        if (stopThrust1 > feint2 || stopThrust1 == feint2) { sumFenc1 += 1; sumFenc2 -= 1; } else { sumFenc1 -= 1; sumFenc2 += 1; }
        if (feint1 > parry2 || feint1 == parry2) { sumFenc1 += 1; sumFenc2 -= 1; } else { sumFenc1 -= 1; sumFenc2 += 1; }

        // Somma totale per abilità
        if (sumFenc1 > sumFenc2) { sumFenc2 = 0; sumFenc1 = 5; } else { sumFenc2 = 5; sumFenc1 = 0; }

        int totalFencer1 = experience1 + speed1 + sumFenc1 - emotional1;
        int totalFencer2 = experience2 + speed2 + sumFenc2 - emotional2;

        if(!direct){
            double percentage = 0;
            if (totalFencer1 > totalFencer2) {
                percentage = (double) totalFencer2 / totalFencer1;
            } else {
                percentage = (double) totalFencer1 / totalFencer2;
            }

            int loserScore;
            if (percentage >= 0.8) {
                loserScore = 4;
            } else if (percentage >= 0.6) {
                loserScore = 3;
            } else if (percentage >= 0.4) {
                loserScore = 2;
            } else if (percentage >= 0.2) {
                loserScore = 1;
            } else {
                loserScore = 0;
            }

            if (totalFencer1 > totalFencer2) {
                totalFencer1 = 5;
                totalFencer2 = loserScore;
            }else{
                totalFencer1 = loserScore;
                totalFencer2 = 5;
            }

            return fencer1 + "," + totalFencer1 + "," + fencer2 + "," + totalFencer2;

        }else{
            if (totalFencer1 > totalFencer2) {
                return fencer1;
            }else{
                return fencer2;
            }
        }
    }
}