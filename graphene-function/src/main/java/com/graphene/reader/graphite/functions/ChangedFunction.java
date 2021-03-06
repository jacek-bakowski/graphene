package com.graphene.reader.graphite.functions;

import com.graphene.reader.beans.TimeSeries;
import com.graphene.reader.exceptions.EvaluationException;
import com.graphene.reader.exceptions.InvalidArgumentException;
import com.graphene.reader.exceptions.TimeSeriesNotAlignedException;
import com.graphene.reader.graphite.Target;
import com.graphene.reader.graphite.evaluation.TargetEvaluator;
import com.graphene.reader.utils.TimeSeriesUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Andrei Ivanov
 */
public class ChangedFunction extends GrapheneFunction {

    public ChangedFunction(String text) {
        super(text, "offset");
    }

    @Override
    public List<TimeSeries> evaluate(TargetEvaluator evaluator) throws EvaluationException {
        List<TimeSeries> processedArguments = new ArrayList<>();
        processedArguments.addAll(evaluator.eval((Target) arguments.get(0)));

        if (processedArguments.size() == 0) return new ArrayList<>();

        if (!TimeSeriesUtils.checkAlignment(processedArguments)) {
            throw new TimeSeriesNotAlignedException();
        }

        int length = processedArguments.get(0).getValues().length;

        for (TimeSeries ts : processedArguments) {
            Double previous = null;
            for (int i = 0; i < length; i++) {
                if (previous == null) {
                    previous = ts.getValues()[i];
                    ts.getValues()[i] = 0.;
                } else if (ts.getValues()[i] != null && !ts.getValues()[i].equals(previous)) {
                    previous = ts.getValues()[i];
                    ts.getValues()[i] = 1.;
                } else {
                    ts.getValues()[i] = 0.;
                }
            }
            ts.setName("changed(" + ts.getName() + ")");
        }

        return processedArguments;
    }

    @Override
    public void checkArguments() throws InvalidArgumentException {
        if (arguments.size() > 1 || arguments.size() < 1) throw new InvalidArgumentException("offset: number of arguments is " + arguments.size() + ". Must be one.");
        if (!(arguments.get(0) instanceof Target)) throw new InvalidArgumentException("offset: argument is " + arguments.get(0).getClass().getName() + ". Must be series");
    }
}
