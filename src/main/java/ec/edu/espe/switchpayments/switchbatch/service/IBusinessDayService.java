package ec.edu.espe.switchpayments.switchbatch.service;

import java.time.LocalDate;

public interface IBusinessDayService {

    LocalDate nextBusinessDay(LocalDate fromExclusive);

    boolean isBusinessDay(LocalDate date);
}
