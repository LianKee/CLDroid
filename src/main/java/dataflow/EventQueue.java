package dataflow;


import java.util.Iterator;
import java.util.LinkedList;
import java.util.Queue;

public class EventQueue {

    private Queue<Event> eventQueue = new LinkedList<>();

    public boolean isEmpty() {
        return eventQueue.isEmpty();
    }

    public boolean add(Event event) {
        return eventQueue.add(event);
    }

    public Event poll() {
        return eventQueue.poll();
    }

    public boolean contains(Event event) {
        return eventQueue.contains(event);
    }

    public Queue<Event> getEventQueue() {
        return eventQueue;
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof EventQueue))
            return false;

        EventQueue eventQueue = (EventQueue) obj;
        return this.eventQueue.equals(eventQueue.getEventQueue());
    }

    @Override
    public int hashCode() {
        return eventQueue.hashCode();
    }

    public int size(){
        return eventQueue.size();
    }
}
