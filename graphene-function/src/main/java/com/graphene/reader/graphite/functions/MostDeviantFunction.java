package com.graphene.reader.graphite.functions;

import com.graphene.reader.exceptions.EvaluationException;
import com.graphene.reader.exceptions.InvalidArgumentException;
import com.graphene.reader.exceptions.TimeSeriesNotAlignedException;
import com.graphene.reader.graphite.Target;
import com.graphene.reader.graphite.evaluation.TargetEvaluator;
import com.graphene.reader.utils.CollectionUtils;
import com.graphene.reader.utils.TimeSeriesUtils;
import com.graphene.reader.beans.TimeSeries;

import java.util.*;

/**
 * @author Andrei Ivanov
 */
public class MostDeviantFunction extends GrapheneFunction {


    public MostDeviantFunction(String text) {
        super(text, "mostDeviant");
    }

    @Override
    public List<TimeSeries> evaluate(TargetEvaluator evaluator) throws EvaluationException {
        int number = ((Double) arguments.get(1)).intValue();
        if (number <= 0) return Collections.emptyList();

        List<TimeSeries> processedArguments = new ArrayList<>();
        processedArguments.addAll(evaluator.eval((Target) arguments.get(0)));

        if (processedArguments.size() == 0) return new ArrayList<>();

        if (!TimeSeriesUtils.checkAlignment(processedArguments)) {
            throw new TimeSeriesNotAlignedException();
        }


        SortedMap<Double, List<TimeSeries>> sorted = new TreeMap<>(Collections.reverseOrder());


        for(TimeSeries ts : processedArguments) {
            Double stdev = CollectionUtils.stdev(Arrays.asList(ts.getValues()));
            if (stdev != null) {
                if (sorted.get(stdev) == null) sorted.put(stdev, new ArrayList<TimeSeries>());
                sorted.get(stdev).add(ts);
            }
        }

        List<TimeSeries> result = new ArrayList<>();

        for(Map.Entry<Double, List<TimeSeries>> entry : sorted.entrySet()) {
            for (TimeSeries ts : entry.getValue()) {
                result.add(ts);
            }
            if (result.size() >= number) break;
        }

        return result.subList(0, Math.min(number, result.size()));
    }

    @Override
    public void checkArguments() throws InvalidArgumentException {
        if (arguments.size() != 2) throw new InvalidArgumentException("mostDeviant: number of arguments is " + arguments.size() + ". Must be one.");
        if (!(arguments.get(0) instanceof Target)) throw new InvalidArgumentException("mostDeviant: argument is " + arguments.get(0).getClass().getName() + ". Must be series");
        if (!(arguments.get(1) instanceof Double)) throw new InvalidArgumentException("mostDeviant: argument is " + arguments.get(1).getClass().getName() + ". Must be a number");
    }
}
