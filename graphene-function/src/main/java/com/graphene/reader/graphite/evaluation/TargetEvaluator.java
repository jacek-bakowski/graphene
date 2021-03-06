package com.graphene.reader.graphite.evaluation;

import com.google.common.collect.Lists;
import com.google.common.collect.ObjectArrays;
import com.graphene.reader.beans.TimeSeries;
import com.graphene.reader.config.Rollup;
import com.graphene.reader.exceptions.EvaluationException;
import com.graphene.reader.exceptions.InvalidNumberOfSeriesException;
import com.graphene.reader.exceptions.TimeSeriesNotAlignedException;
import com.graphene.reader.exceptions.TooMuchDataExpectedException;
import com.graphene.reader.graphite.PathTarget;
import com.graphene.reader.graphite.Target;
import com.graphene.reader.graphite.functions.GrapheneFunction;
import com.graphene.reader.service.index.KeySearchHandler;
import com.graphene.reader.service.metric.MetricService;
import com.graphene.reader.utils.TimeSeriesUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;

/** @author Andrei Ivanov */
public class TargetEvaluator {
  static final Logger logger = LogManager.getLogger(TargetEvaluator.class);

  private MetricService metricService;
  private KeySearchHandler keySearchHandler;

  public TargetEvaluator(MetricService metricService, KeySearchHandler keySearchHandler) {
    this.metricService = metricService;
    this.keySearchHandler = keySearchHandler;
  }

  public List<TimeSeries> eval(Target target) throws EvaluationException {
    return target.evaluate(this);
  }

  public List<List<TimeSeries>> evalByGroup(Target target) throws  EvaluationException {
      return target.evalByGroup(this);
  }

  public List<TimeSeries> visit(PathTarget pathTarget) throws EvaluationException {
    try {
      Set<String> paths =
          keySearchHandler.getPaths(pathTarget.getTenant(), Lists.newArrayList(pathTarget.getPath()), pathTarget.getFrom(), pathTarget.getTo());

      logger.debug("resolved paths : " + paths);

      return metricService.getMetricsAsList(
          pathTarget.getTenant(),
          paths,
          pathTarget.getFrom(),
          pathTarget.getTo());
    } catch (ExecutionException | InterruptedException | TooMuchDataExpectedException e) {
      logger.error(e.getMessage());
      logger.debug(e);
      throw new EvaluationException(e);
    }
  }

  public List<TimeSeries> visit(GrapheneFunction function) throws EvaluationException {
    return function.evaluate(this);
  }

  // todo: the logic below is duplicated several times - fix it!
  public TimeSeries getEmptyTimeSeries(long from, long to) {
    Long now = System.currentTimeMillis() * 1000;
    Long effectiveTo = Math.min(to, now);
    Rollup bestRollup = metricService.getRollup(from);
    Long effectiveFrom =
        (from % bestRollup.getRollup()) == 0
            ? from
            : from + bestRollup.getRollup() - (from % bestRollup.getRollup());
    effectiveTo = effectiveTo - (effectiveTo % bestRollup.getRollup());

    int length = (int) ((effectiveTo - effectiveFrom) / bestRollup.getRollup() + 1);

    TimeSeries ts = new TimeSeries("", effectiveFrom, effectiveTo, bestRollup.getRollup());
    ts.setValues(new Double[length]);

    return ts;
  }

  // todo: suboptimal
  public List<TimeSeries> bootstrap(Target target, List<TimeSeries> original, long period)
      throws EvaluationException {
    if (original.size() == 0) return new ArrayList<>();

    List<TimeSeries> bootstrapped = new ArrayList<>();
    bootstrapped.addAll(eval(target.previous(period)));

    if (bootstrapped.size() != original.size()) throw new InvalidNumberOfSeriesException();
    if (!TimeSeriesUtils.checkAlignment(bootstrapped)) throw new TimeSeriesNotAlignedException();

    int step = original.get(0).getStep();

    // normalize (assuming bootstrapped step can only be bigger
    if (bootstrapped.get(0).getStep() != step) {
      int ratio = bootstrapped.get(0).getStep() / step;
      for (TimeSeries ts : bootstrapped) {
        List<Double> values = new ArrayList<>();
        for (int i = 0; i < ts.getValues().length; i++) {
          values.addAll(Collections.nCopies(ratio, ts.getValues()[i]));
        }
        ts.setValues(values.toArray(new Double[values.size()]));
      }
    }

    for (int i = 0; i < bootstrapped.size(); i++) {
      bootstrapped
          .get(i)
          .setValues(
              ObjectArrays.concat(
                  bootstrapped.get(i).getValues(), original.get(i).getValues(), Double.class));
      bootstrapped.get(i).setStep(step);
    }

    return bootstrapped;
  }
}
