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
 * of this source code, which includeas information that is confidential and/or proprietary, and
 * is a trade secret, of ShiftLeft, Inc.
 *
 * ANY REPRODUCTION, MODIFICATION, DISTRIBUTION, PUBLIC PERFORMANCE, OR PUBLIC DISPLAY
 * OF OR THROUGH USE OF THIS SOURCE CODE WITHOUT THE EXPRESS WRITTEN CONSENT OF ShiftLeft, Inc.
 * IS STRICTLY PROHIBITED, AND IN VIOLATION OF APPLICABLE LAWS AND INTERNATIONAL TREATIES.
 * THE RECEIPT OR POSSESSION OF THIS SOURCE CODE AND/OR RELATED INFORMATION DOES NOT
 * CONVEY OR IMPLY ANY RIGHTS TO REPRODUCE, DISCLOSE OR DISTRIBUTE ITS
 * CONTENTS, OR TO MANUFACTURE, USE, OR SELL ANYTHING THAT IT MAY DESCRIBE, IN WHOLE OR IN PART.
 */
package io.shiftleft.bctrace;

import io.shiftleft.bctrace.asm.Transformer;
import java.lang.instrument.UnmodifiableClassException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import io.shiftleft.bctrace.runtime.Callback;
import io.shiftleft.bctrace.runtime.InstrumentationImpl;
import io.shiftleft.bctrace.spi.Hook;

/**
 * Framework entry point.
 *
 * @author Ignacio del Valle Alles idelvall@shiftleft.io
 */
public final class Bctrace {

  private static final ThreadLocal<Boolean> NOTIFYING_DISABLED_FLAG = new ThreadLocal<Boolean>();

  static Bctrace instance;

  private final Transformer transformer;
  private final List<Hook> hooks;
  private final java.lang.instrument.Instrumentation javaInstrumentation;

  Bctrace(java.lang.instrument.Instrumentation javaInstrumentation, Hook[] initialHooks) {
    this.javaInstrumentation = javaInstrumentation;
    this.hooks = Collections.synchronizedList(new ArrayList<Hook>());
    this.transformer = new Transformer();
    if (initialHooks != null) {
      for (Hook initialHook : initialHooks) {
        this.addHook(initialHook);
      }
    }
    this.javaInstrumentation.addTransformer(transformer, javaInstrumentation.isRetransformClassesSupported());
  }

  public static Bctrace getInstance() {
    if (instance == null) {
      throw new Error("Instrumentation is not properly configured. Please verify agent manifest attributes");
    }
    return instance;
  }

  @SuppressWarnings("BoxedValueEquality")
  public static boolean isThreadNotificationEnabled() {
    return NOTIFYING_DISABLED_FLAG.get() != Boolean.TRUE;
  }

  @SuppressWarnings("BoxedValueEquality")
  public static void enableThreadNotification() {
    NOTIFYING_DISABLED_FLAG.remove();
  }

  @SuppressWarnings("BoxedValueEquality")
  public static void disableThreadNotification() {
    NOTIFYING_DISABLED_FLAG.set(Boolean.TRUE);
  }

  public void addHook(Hook hook) {
    this.hooks.add(hook);
    this.updateCallback();
    hook.init(new InstrumentationImpl(javaInstrumentation));
  }

  public void removeHook(Hook hook) {
    this.removeHook(hook, true);
  }

  public void removeHook(Hook hook, boolean retransform) {
    int index = this.hooks.indexOf(hook);
    this.hooks.set(index, null);
    if (retransform) {
      try {
        hook.getInstrumentation().retransformClasses(hook.getInstrumentation().getTransformedClasses());
      } catch (UnmodifiableClassException ex) {
        throw new AssertionError();
      }
    }
    this.updateCallback();
  }

  private void updateCallback() {
    Callback.hooks = hooks.toArray(new Hook[hooks.size()]);
  }

}
