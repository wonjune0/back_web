package com.wonjune.backweb.stock;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "stock")
@Getter
@Setter
public class StockProperties {

	private StockStrategy strategy = StockStrategy.ATOMIC;

}
