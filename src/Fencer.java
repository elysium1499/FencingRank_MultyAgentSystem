import jade.core.Agent;
import jade.core.behaviours.CyclicBehaviour;
import jade.lang.acl.ACLMessage;

public class Fencer extends Agent {
    public String name;
    public String experience;
    public String speed;
    public String parry;
    public String stopTrust;
    public String feint;
    public String emotion;
    public String rank;

    protected void setup() {
        Object[] fencerField = getArguments();
        if (fencerField != null && fencerField.length == 8) {
            name = (String) fencerField[0];
            experience = (String) fencerField[1];
            speed = (String) fencerField[2];
            parry = (String) fencerField[3];
            stopTrust = (String) fencerField[4];
            feint = (String) fencerField[5];
            emotion = (String) fencerField[6];
            rank = (String) fencerField[7];

        } else {
            System.out.println("CSV column miss: " + getLocalName());
        }

        addBehaviour(new HandleBout());
    }

    private class HandleBout extends CyclicBehaviour {
        private boolean registered = false;

        public void action() {
            ACLMessage mex = receive();
            if (mex != null) {
                // Registration confirm
                if (!registered && mex.getPerformative() == ACLMessage.CONFIRM) {
                    registered = true;
                    System.out.println("Registered: " + name);
                }
                
                //bout information: receive referee message about bout
                if (registered && mex.getPerformative() == ACLMessage.INFORM && "bout".equals(mex.getConversationId())) {
                    // answer to referee with ability data
                    ACLMessage reply = mex.createReply();
                    reply.setPerformative(ACLMessage.INFORM);
                    reply.setConversationId("ability");
                    reply.setContent(name + "," + experience + "," + speed + "," + parry + "," + stopTrust + "," + feint + "," + emotion);
                    send(reply);
                }

                //elimination: partecipate or not at next step
                if (registered && mex.getPerformative() == ACLMessage.INFORM && "elimination".equals(mex.getConversationId())) {
                    doDelete();
                }
            } else {
                ACLMessage mess = new ACLMessage(ACLMessage.INFORM);
                mess.addReceiver(new jade.core.AID("organizer", jade.core.AID.ISLOCALNAME));
                mess.setContent(getAID().getLocalName() + "," + name + "," + rank);
                send(mess);

                block();
            }
        }
    }
}
