/*
 * ShiftLeft, Inc. CONFIDENTIAL
 * Unpublished Copyright (c) 2017 ShiftLeft, Inc., All Rights Reserved.
 *
 * NOTICE: All information contained herein is, and remains the property of ShiftLeft, Inc.
 * The intellectual and technical concepts contained herein are proprietary to ShiftLeft, Inc.
 * and may be covered by U.S. and Foreign Patents, patents in process, and are protected by
 * trade secret or copyright law. Dissemination of this information or reproduction of this
 * material is strictly forbidden unless prior written permission is obtained
 * from ShiftLeft, Inc. Access to the source code contained herein is hereby forbidden to
 * anyone except current ShiftLeft, Inc. employees, managers or contractors who have executed
 * Confidentiality and Non-disclosure agreements explicitly covering such access.
 *
 * The copyright notice above does not evidence any actual or intended publication or disclosure
 * of this source code, which includes information that is confidential and/or proprietary, and
 * is a trade secret, of ShiftLeft, Inc.
 *
 * ANY REPRODUCTION, MODIFICATION, DISTRIBUTION, PUBLIC PERFORMANCE, OR PUBLIC DISPLAY
 * OF OR THROUGH USE OF THIS SOURCE CODE WITHOUT THE EXPRESS WRITTEN CONSENT OF ShiftLeft, Inc.
 * IS STRICTLY PROHIBITED, AND IN VIOLATION OF APPLICABLE LAWS AND INTERNATIONAL TREATIES.
 * THE RECEIPT OR POSSESSION OF THIS SOURCE CODE AND/OR RELATED INFORMATION DOES NOT
 * CONVEY OR IMPLY ANY RIGHTS TO REPRODUCE, DISCLOSE OR DISTRIBUTE ITS
 * CONTENTS, OR TO MANUFACTURE, USE, OR SELL ANYTHING THAT IT MAY DESCRIBE, IN WHOLE OR IN PART.
 */
package io.shiftleft.bctrace.generic;

import static org.junit.Assert.assertEquals;

import io.shiftleft.bctrace.BcTraceTest;
import io.shiftleft.bctrace.TestClass;
import io.shiftleft.bctrace.filter.AllFilter;
import io.shiftleft.bctrace.filter.Filter;
import io.shiftleft.bctrace.hook.Hook;
import io.shiftleft.bctrace.hook.generic.GenericHook;
import io.shiftleft.bctrace.runtime.listener.generic.Disabled;
import io.shiftleft.bctrace.runtime.listener.generic.GenericListener;
import io.shiftleft.bctrace.runtime.listener.generic.StartListener;
import org.junit.Test;

/**
 * @author Ignacio del Valle Alles idelvall@shiftleft.io
 */
public class StartListenerTest extends BcTraceTest {

  @Test
  public void testStart() throws Exception {
    final StringBuilder steps = new StringBuilder();
    Class clazz = getInstrumentClass(TestClass.class, new Hook[]{
        new GenericHook() {
          @Override
          public Filter getFilter() {
            return new AllFilter();
          }

          @Override
          public GenericListener getListener() {
            return new StartListener() {
              @Override
              public void onStart(int methodId, Class clazz, Object instance, Object[] args) {
                assertEquals(clazz.getName(), TestClass.class.getName());
                steps.append("1");
              }
            };
          }
        },
        new GenericHook() {
          @Override
          public Filter getFilter() {
            return new AllFilter();
          }

          @Override
          public GenericListener getListener() {
            return new StartListener() {

              @Override
              public void onStart(int methodId, Class clazz, Object instance, Object[] args) {
                assertEquals(clazz.getName(), TestClass.class.getName());
                steps.append("2");
              }
            };
          }
        }
    });
    clazz.getMethod("execVoid").invoke(null);
    System.out.println(clazz.getClassLoader());
    assertEquals("12", steps.toString());
  }

  @Test
  public void testStartDisabled() throws Exception {
    final StringBuilder steps = new StringBuilder();
    Class clazz = getInstrumentClass(TestClass.class, new Hook[]{
        new GenericHook() {
          @Override
          public Filter getFilter() {
            return new AllFilter();
          }

          @Override
          public GenericListener getListener() {
            return new StartListener() {
              @Override
              public void onStart(@Disabled int methodId, @Disabled Class clazz,
                  @Disabled Object instance, @Disabled Object[] args) {
                if (clazz == null && args == null) {
                  steps.append("1");
                }
              }
            };
          }
        }
    });
    clazz.getMethod("execVoid").invoke(null);
    assertEquals("1", steps.toString());
  }
}
