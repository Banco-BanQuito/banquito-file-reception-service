package ec.edu.espe.Switch.Batch.service;

import java.time.LocalDate;

public interface IBusinessDayService {

    LocalDate nextBusinessDay(LocalDate fromExclusive);

    boolean isBusinessDay(LocalDate date);
}
