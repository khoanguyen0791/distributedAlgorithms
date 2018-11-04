package floodMax;

import java.util.ArrayList;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;

import message.Message;

/**
 * MasterNode is a special case of a SlaveNode.
 * 
 * @author khoa
 *
 */
public class MasterThread extends SlaveThread {

  protected int masterId = 0;
  protected int size;
  protected int[] slaveArray;
  protected double[][] matrix;

  protected boolean masterMustDie = false;

  private int numberOfFinishedThreads;
  protected ConcurrentHashMap<Integer, LinkedBlockingQueue<Message>> globalIdAndMsgQueueMap;
  private ArrayList<SlaveThread> threadList = new ArrayList<SlaveThread>();

  /**
   * Constructor
   * 
   * @param size
   * @param slaveArray
   * @param matrix
   */
  public MasterThread(int size, int[] slaveArray, double[][] matrix) {
    this.round = 1;
    this.numberOfFinishedThreads = 0;
    this.size = size;
    this.slaveArray = slaveArray;
    this.matrix = matrix;
    this.name = "Master";
    this.id = 0;

    this.globalIdAndMsgQueueMap = new ConcurrentHashMap<Integer, LinkedBlockingQueue<Message>>();
    this.localMessagesToSend = new ConcurrentHashMap<>();
    initGlobalIdAndMsgQueueMap();
  }

  @Override
  public void initLocalMessagesToSend() {
    localMessagesToSend.put(id, new LinkedBlockingQueue<Message>());
    for (int i : slaveArray) {
      localMessagesToSend.put(i, new LinkedBlockingQueue<Message>());
    }
  }

  /**
   * First step of master thread: fill the global queue.
   */
  public void initGlobalIdAndMsgQueueMap() {
    globalIdAndMsgQueueMap.put(0, new LinkedBlockingQueue<Message>());
    for (int i : slaveArray) {
      globalIdAndMsgQueueMap.put(i, new LinkedBlockingQueue<Message>());
    }
  }

  @Override
  public void run() {
    System.out.println("The Master has started. Size: " + size);
    createThreads();
    sendRoundStartMsg();

    do {
      for (SlaveThread t : threadList) {
        t.drainToGlobalQueue();
      }

      globalIdAndMsgQueueMap.get(id).drainTo(localMessageQueue);
      System.out
          .println("Master checking its queue. Size of queue is: " + localMessageQueue.size() + " round " + round);

      while (!(localMessageQueue.isEmpty())) {
        try {
          Message tempMsg = localMessageQueue.take();
          // System.out.println(tempMsg);

          if (tempMsg.getmType().equalsIgnoreCase("Leader")) {
            // if a node says it's Leader to master, master tells the node to terminate.
            localMessageQueue.clear();

            globalIdAndMsgQueueMap.get(tempMsg.getSenderId()).put(new Message(id, 0, round, maxUid, "Terminate"));
            System.err.println("---Telling the master to die. Leader is: " + tempMsg.getSenderId() + " round " + round);
            masterMustDie = true;

            maxUid = tempMsg.getSenderId();
            killAll();
          } else if ((tempMsg.getmType().equals("Done"))) {
            numberOfFinishedThreads++;
          }
          // all slaves completed the round
          if (numberOfFinishedThreads == size) {
            round++;

            // if all slaves completed the round, master tells nodes to start next round
            sendRoundStartMsg();
            // Reset Done Count for next round messages
            numberOfFinishedThreads = 0;
          }
        } catch (Exception e) {
          e.printStackTrace();
        }

      }
      System.out.println("Starting threads. ");
      startAllThreads();
    } while (!masterMustDie);

    printTree();
    printNackAckTree();
    System.out.println("Master will now die. MaxUid " + maxUid + " round " + round);

  }

  /**
   * Print the nackAck set after the algorithm is done.
   */
  public void printTree() {
    System.out.println("\n\nPrinting the tree.");
    System.out.println("MaxId----Parent <--- myId ---> myChildren (can overlap)");
    for (SlaveThread t : threadList) {
      System.out.print(t.maxUid + "---  " + t.getMyParent() + "<------" + t.getId() + "------>");
      for (Entry<Integer, Double> i : t.neighborMap.entrySet()) {
        if (i.getKey() != t.getMyParent()) {
          System.out.print(i + " ");
        }
      }
      System.out.println();
    }
  }

  /**
   * Create all nodes/threads, set their names, neighbors, initiate the local copy
   * of the global queue.
   */
  public void createThreads() {
    try {
      for (int row = 0; row < size; row++) {
        SlaveThread t = new SlaveThread(slaveArray[row], this, globalIdAndMsgQueueMap);
        threadList.add(t);

        for (int col = 0; col < size; col++)
          if (matrix[row][col] != 0) {
            t.insertNeighbour(slaveArray[col], matrix[row][col]);
          }
        t.initLocalMessagesToSend();
      }

      System.err.println("Created threads. ");
    } catch (Exception err) {
      err.printStackTrace();
    }
  }

  public void startAllThreads() {
    for (SlaveThread t : threadList) {
      t.run();
    }
  }

  public void sendRoundStartMsg() {
    for (int i : slaveArray) {

      // don't send msg to itself
      if (i == id) {
        continue;
      }

      // System.err.println("Send Round_Number msg to " + i);
      try {
        Message temp = new Message(id, 0, round, id, "Round_Number");
        globalIdAndMsgQueueMap.get(i).put(temp);

      } catch (Exception e) {
        e.printStackTrace();
      }
    }
  }

  public void checkGlobalQueueNotEmpty() {
    for (Entry<Integer, LinkedBlockingQueue<Message>> p : globalIdAndMsgQueueMap.entrySet()) {
      System.out.println(p.getKey() + " " + p.getValue());
    }
  }

  public void killAll() {
    for (int i : slaveArray) {

      // don't send msg to itself
      if (i == id) {
        continue;
      }

      System.out.println("Send Terminate msg to " + i);
      try {
        Message temp = new Message(id, 0, round, id, "Terminate");
        globalIdAndMsgQueueMap.get(i).put(temp);

      } catch (Exception e) {
        e.printStackTrace();
      }
    }
  }
}