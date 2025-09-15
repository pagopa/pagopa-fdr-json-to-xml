package it.gov.pagopa.fdr.conversion.util;

import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.RetryContext;
import com.microsoft.azure.functions.RpcException;

import java.util.logging.Logger;

public class Utils {
    public static ExecutionContext createContext(int retry) {
        return new ExecutionContext() {
            @Override
            public Logger getLogger() {
                return  Logger.getLogger("test");
            }

            @Override
            public String getInvocationId() {
                return "test-invocation-id";
            }

            @Override
            public String getFunctionName() {
                return "test-function-name";
            }

            @Override
            public RetryContext getRetryContext() {
                return new RetryContext() {
                    @Override
                    public int getRetrycount() {
                        return retry;
                    }

                    @Override
                    public int getMaxretrycount() {
                        return 100;
                    }

                    @Override
                    public RpcException getException() {
                        return null;
                    }
                };
            }
        };
    }
}
