package org.simpleframework.xml.transform;

import org.ice4j.ice.CandidateType;

public class IceCandidateTypeTransform implements Transform<CandidateType> {

  @Override
  public CandidateType read(String value) throws Exception {
    return CandidateType.parse(value);
  }

  @Override
  public String write(CandidateType value) throws Exception {
    return value.toString();
  }
}