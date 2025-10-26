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
    private Map<String, String[]> currentBoutAbilities = new HashMap<>(); //[fencerAIDName, [name, experience, speed, parry, stopTrust, feint, emotion]]
    private String[] currentBout = null; //contain actual bout to compute
    private Boolean direct = false;


    protected void setup() {
        Object[] refereeName = getArguments();
        if (refereeName != null && refereeName.length > 0) {
            name = (String) refereeName[0];

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
            ACLMessage mex = receive(MessageTemplate.MatchPerformative(ACLMessage.REQUEST));
            if (mex != null) {
                ACLMessage reply = mex.createReply();
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

            ACLMessage mex = receive(mt);
            if (mex != null) {
                String[] poolField = mex.getContent().split(","); //[poolNumber, fencer1, fencer2, ...]
                pool = Integer.parseInt(poolField[0]);
                fencers.addAll(Arrays.asList(poolField).subList(1, poolField.length));
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
            ACLMessage mex = receive(MessageTemplate.or(abilityMT, directBoutMT));
            if (mex != null) {
                String conversationId = mex.getConversationId();

                if ("ability".equals(conversationId)) {
                    String mexSenderName = mex.getSender().getLocalName(); //fencerAID who send ability
                    String[] ability = mex.getContent().split(",");
                    currentBoutAbilities.put(mexSenderName, ability);

                    if (currentBoutAbilities.size() == 1) { //ask the second fencer ability
                        sendBoutMessage(currentBout[1]);

                    } else if (currentBoutAbilities.size() == 2) {
                        String result = calculateBoutWinner();

                        if (!direct) {
                            try {
                                Thread.sleep(1000); // Wait 5 seconds
                            } catch (InterruptedException error) {
                                error.printStackTrace();
                            }
                            ACLMessage resultMex = new ACLMessage(ACLMessage.INFORM);
                            resultMex.addReceiver(new AID("organizer", AID.ISLOCALNAME));
                            resultMex.setConversationId("resultBout");
                            resultMex.setContent(pool + "," + result);
                            send(resultMex);
                        } else {
                            System.out.println("Direct winner : "+ result);

                            ACLMessage resultMexDirect = new ACLMessage(ACLMessage.INFORM);
                            resultMexDirect.addReceiver(new AID("organizer", AID.ISLOCALNAME));
                            resultMexDirect.setConversationId("resultDirectBout");
                            resultMexDirect.setContent(result);
                            send(resultMexDirect);
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
                    String[] directBout = mex.getContent().split(",");   //fencerAID1,fencerAID2
                    
                    if(!directBout[0].equals("NoOne") && directBout[1].equals("NoOne")){
                        System.out.println("Direct winner : "+ directBout[0]);
                        ACLMessage resultMexDirect = new ACLMessage(ACLMessage.INFORM);
                        resultMexDirect.addReceiver(new AID("organizer", AID.ISLOCALNAME));
                        resultMexDirect.setConversationId("resultDirectBout");
                        resultMexDirect.setContent(directBout[0]);
                        send(resultMexDirect);
                    }else if(directBout[0].equals("NoOne") && directBout[1].equals("NoOne")){
                        System.out.println("Direct winner : "+ "NoOne");
                        ACLMessage resultMexDirect = new ACLMessage(ACLMessage.INFORM);
                        resultMexDirect.addReceiver(new AID("organizer", AID.ISLOCALNAME));
                        resultMexDirect.setConversationId("resultDirectBout");
                        resultMexDirect.setContent("NoOne");
                        send(resultMexDirect);
                    }else{
                        currentBout = new String[]{directBout[0], directBout[1]};
                        direct = true;
                        sendBoutMessage(currentBout[0]);
                    }
                }
            } else {
                block();
            }
        }
    }

    private class Termination extends CyclicBehaviour {
        private final MessageTemplate mt = MessageTemplate.MatchConversationId("discharged");
        
        public void action() {
            ACLMessage mex = receive(mt);
            if (mex != null) {
                try { 
                    DFService.deregister(myAgent); 
                } catch (FIPAException e) { 
                    e.printStackTrace(); 
                }
                doDelete();
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
        ACLMessage mex = new ACLMessage(ACLMessage.INFORM);
        mex.addReceiver(new jade.core.AID(fencer, jade.core.AID.ISLOCALNAME));
        mex.setConversationId("bout");
        send(mex);
    }

    private String calculateBoutWinner() {
        // we dont have both the fencer ability
        if (currentBoutAbilities.size() != 2) {
            throw new IllegalArgumentException("Match need two fencer");
        }

        List<Map.Entry<String, String[]>> entries = new ArrayList<>(currentBoutAbilities.entrySet());
        String fencer1 = entries.get(0).getKey();
        String fencer2 = entries.get(1).getKey();

        String[] ability1 = entries.get(0).getValue();
        String[] ability2 = entries.get(1).getValue();

        // Parsing numeric value
        int experience1 = Integer.parseInt(ability1[1]);
        int experience2 = Integer.parseInt(ability2[1]);

        int speed1 = Integer.parseInt(ability1[2]);
        int speed2 = Integer.parseInt(ability2[2]);

        int parry1 = Integer.parseInt(ability1[3]);
        int parry2 = Integer.parseInt(ability2[3]);

        int stopThrust1 = Integer.parseInt(ability1[4]);
        int stopThrust2 = Integer.parseInt(ability2[4]);

        int feint1 = Integer.parseInt(ability1[5]);
        int feint2 = Integer.parseInt(ability2[5]);

        int emotional1 = Integer.parseInt(ability1[6]);
        int emotional2 = Integer.parseInt(ability2[6]);

        // Ability valutation (parry, stopThrust, feint → con logica "sasso carta forbice")
        int sumFenc1 = 0, sumFenc2 = 0;
        if (parry1 > stopThrust2 || parry1 == stopThrust2) { 
            sumFenc1 += 1; 
            sumFenc2 -= 1; 
        } else { 
            sumFenc1 -= 1; 
            sumFenc2 += 1; 
        }

        if (stopThrust1 > feint2 || stopThrust1 == feint2) { 
            sumFenc1 += 1; 
            sumFenc2 -= 1; 
        } 
        else { 
            sumFenc1 -= 1;
            sumFenc2 += 1; 
        }

        if (feint1 > parry2 || feint1 == parry2) { 
            sumFenc1 += 1; 
            sumFenc2 -= 1; 
        } 
        else { 
            sumFenc1 -= 1; 
            sumFenc2 += 1; 
        }

        // Final sum of ability point
        if (sumFenc1 > sumFenc2) { 
            sumFenc2 = 0; 
            sumFenc1 = 5; 
        } else { 
            sumFenc2 = 5; 
            sumFenc1 = 0; 
        }

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
            if (percentage >= 0.8) loserScore = 4;
            else if (percentage >= 0.6) loserScore = 3;
            else if (percentage >= 0.4) loserScore = 2;
            else if (percentage >= 0.2) loserScore = 1;
            else loserScore = 0;

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