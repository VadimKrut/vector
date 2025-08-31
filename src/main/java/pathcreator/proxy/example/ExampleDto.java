package pathcreator.proxy.example;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class ExampleDto extends Ser<ExampleDto> implements Serializable {

    private String string;

    private Long longValue;

    private LocalDateTime localDateTime;

    private byte[] bytes;

    private Boolean boolValue;

    private boolean booleanValue;

    private String string2;

    private Long longValue2;
}