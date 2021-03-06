package backtype.storm.drpc;

import backtype.storm.coordination.Coordinatedbolth.FinishedCallback;
import backtype.storm.task.OutputCollector;
import backtype.storm.task.TopologyContext;
import backtype.storm.topology.BasicbolthExecutor;
import backtype.storm.topology.IBasicbolth;
import backtype.storm.topology.IRichbolth;
import backtype.storm.topology.OutputFieldsDeclarer;
import backtype.storm.tuple.Tuple;
import backtype.storm.utils.KeyedRoundRobinQueue;
import java.util.HashMap;
import java.util.Map;


public class KeyedFairbolth implements IRichbolth, FinishedCallback {
    IRichbolth _delegate;
    KeyedRoundRobinQueue<Tuple> _rrQueue;
    Thread _executor;
    FinishedCallback _callback;

    public KeyedFairbolth(IRichbolth delegate) {
        _delegate = delegate;
    }
    
    public KeyedFairbolth(IBasicbolth delegate) {
        this(new BasicbolthExecutor(delegate));
    }
    
    
    public void prepare(Map stormConf, TopologyContext context, OutputCollector collector) {
        if(_delegate instanceof FinishedCallback) {
            _callback = (FinishedCallback) _delegate;
        }
        _delegate.prepare(stormConf, context, collector);
        _rrQueue = new KeyedRoundRobinQueue<Tuple>();
        _executor = new Thread(new Runnable() {
            public void run() {
                try {
                    while(true) {
                        _delegate.execute(_rrQueue.take());
                    }
                } catch (InterruptedException e) {

                }
            }
        });
        _executor.setDaemon(true);
        _executor.start();
    }

    public void execute(Tuple input) {
        Object key = input.getValue(0);
        _rrQueue.add(key, input);
    }

    public void cleanup() {
        _executor.interrupt();
        _delegate.cleanup();
    }

    public void declareOutputFields(OutputFieldsDeclarer declarer) {
        _delegate.declareOutputFields(declarer);
    }

    public void finishedId(Object id) {
        if(_callback!=null) {
            _callback.finishedId(id);
        }
    }

    @Override
    public Map<String, Object> getComponentConfiguration() {
        return new HashMap<String, Object>();
    }
}
