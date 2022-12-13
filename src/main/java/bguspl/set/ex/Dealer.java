package bguspl.set.ex;

import bguspl.set.Env;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Vector;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.logging.Level;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * This class manages the dealer's threads and data
 */
public class Dealer implements Runnable {

    /**
     * The game environment object.
     */
    private final Env env;

    /**
     * Game entities.
     */
    private final Table table;
    private final Player[] players;

    /**
     * The list of card ids that are left in the dealer's deck.
     */
    private final List<Integer> deck;

    /**
     * True iff game should be terminated due to an external event.
     */
    private volatile boolean terminate;

    /**
     * The time when the dealer needs to reshuffle the deck due to turn timeout.
     */
    private long reshuffleTime = Long.MAX_VALUE;

    private ConcurrentLinkedQueue<Vector<int>> fairnessQueueCards;
    private ConcurrentLinkedQueue<Player> fairnessQueuePlayers;
    private boolean foundSet;
    private Vector<int> theSet = null;

    public Dealer(Env env, Table table, Player[] players) {
        this.env = env;
        this.table = table;
        this.players = players;
        deck = IntStream.range(0, env.config.deckSize).boxed().collect(Collectors.toList());
        fairnessQueueCards = new ConcurrentLinkedQueue<>();
        fairnessQueuePlayers = new ConcurrentLinkedQueue<>();
    }

    /**
     * The dealer thread starts here (main loop for the dealer thread).
     */
    @Override
    public void run() {
        env.logger.log(Level.INFO, "Thread " + Thread.currentThread().getName() + " starting.");
        //how many threads do I need of player? --> create an array of the player threads
        //t1.start();
        while (!shouldFinish()) {
            placeCardsOnTable();
            //TODO fix implementation
            //start threads?
/*            while(fairnessQueueCards!=null & !foundSet){
                checkNextSet();
                if(foundSet){
                    removeCardsFromTable();
                    placeCardsOnTable();
                    updateTimerDisplay(true); //reset countdown
                    sleepUntilWokenOrTimeout(); //player must wake me up?
                }
            } // after I found a set I want the player to choose again*///-->incorrect implementation
            timerLoop();
            updateTimerDisplay(false);
            removeAllCardsFromTable();
        }
        announceWinners();
        env.logger.log(Level.INFO, "Thread " + Thread.currentThread().getName() + " terminated.");
    }



    /**
     * The inner loop of the dealer thread that runs as long as the countdown did not time out.
     */
    private void timerLoop() {
        while (!terminate && System.currentTimeMillis() < reshuffleTime) {
            sleepUntilWokenOrTimeout();
            updateTimerDisplay(false);
            removeCardsFromTable();
            placeCardsOnTable();

        }
    }

    /**
     * Called when the game should be terminated due to an external event.
     */
    public void terminate() {
        terminate = true;
        for(Player p : players){
            p.terminate();
        }
        // TODO not sure if I need to update table + ui
    }

    /**
     * Check if the game should be terminated or the game end conditions are met.
     *
     * @return true iff the game should be finished.
     */
    private boolean shouldFinish() {
        return terminate || env.util.findSets(deck, 1).size() == 0;
    }

    /**
     * Checks cards should be removed from the table and removes them.
     */
    private void removeCardsFromTable() {
        //for each player check the set-->
        //TODO FIXXXXXXXXXXXXXX
        //I need to call the check set here probably
       if(theSet!=null){
           for(int id: theSet){
               table.removeCard(table.cardToSlot[id]);
           }
           foundSet = false;
           theSet = null;
           fairnessQueueCards.clear();
           fairnessQueuePlayers.clear();
       }
    }

    /**
     * Check if any cards can be removed from the deck and placed on the table.
     */
    private void placeCardsOnTable() {
        int min = 0;
        for (int i = 0; i < table.slotToCard.length; i++) {
            if (table.slotToCard[i] == null) {
                int max = deck.size() - 1;
                int random_num = (int)Math.floor(Math.random()*(max-min+1)+min);
                int card = deck.remove(random_num);
                table.placeCard(card, i);
            }
        }
    }
    /**
     * Sleep for a fixed amount of time or until the thread is awakened for some purpose.
     */
    private void sleepUntilWokenOrTimeout() {
        //TODO : CHECK IF CORRECT PROBABLY NOT
        try {
            wait(env.config.tableDelayMillis);
        } catch (InterruptedException ignored) {}
    }

    /**
     * Reset and/or update the countdown and the countdown display.
     */
    private void updateTimerDisplay(boolean reset) {
        env.ui.setCountdown(0,reset);
    }

    /**
     * Returns all the cards from the table to the deck.
     */
    private void removeAllCardsFromTable() {
        for(int i = 0; i < 12; i++) {
            //not sure about the correct index of the slot
            table.removeCard(i);
            deck.add(i);
        }
    }

    public void iGotASet(Player p, Vector<int> cards) {
        fairnessQueueCards.add(cards);
        fairnessQueuePlayers.add(p);
        notifyAll(); //TODO check if this lines notifies all threads waiting for 'this' (as in - waiting for the dealer object to be notified)
    }
    private void checkNextSet() { //changed some things here in order to be able to remove the cards
        try {
            Vector<int> cards = fairnessQueueCards.remove();
            Player p = fairnessQueuePlayers.remove();
            int[] cardsAsArray = new int[cards.size()];
            for (int i = 0; i < cards.size(); i++) {
                cardsAsArray[i] = cards.get(i);
            }
            if (env.util.testSet(cardsAsArray))
            {p.point(); theSet = cards; foundSet = true;}
            else{
                p.penalty(); foundSet = false; theSet = null; }
            cards.notifyAll();

        } catch (NoSuchElementException ignored) {}

    }

    /**
     * Check who is/are the winner/s and displays them.
     */
    private void announceWinners() {
        // collect winning players
        List<Integer> potentialWinners = new Vector<>();
        int maxScore = -1;
        //found the max score
        for(Player p : players){
            if(maxScore < p.getScore()) {
                potentialWinners.clear();
                maxScore = p.getScore();
                potentialWinners.add(p.id);
            }
            else if (maxScore == p.getScore())
                potentialWinners.add(p.id);
        }
        //created an array
        int [] winners = new int[potentialWinners.size()];
        for(int i = 0; i < potentialWinners.size(); i++){
            winners[i] = potentialWinners.get(i);
        }

        env.ui.announceWinner(winners);
    }
}
