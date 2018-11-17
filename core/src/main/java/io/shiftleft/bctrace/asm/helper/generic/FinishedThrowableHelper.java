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
package io.shiftleft.bctrace.asm.helper.generic;

import com.sun.org.apache.bcel.internal.generic.INVOKESPECIAL;
import io.shiftleft.bctrace.Bctrace;
import io.shiftleft.bctrace.asm.helper.Helper;
import io.shiftleft.bctrace.asm.util.ASMUtils;
import io.shiftleft.bctrace.logging.Level;
import io.shiftleft.bctrace.runtime.listener.generic.FinishedThrowableListener;
import java.util.ArrayList;
import java.util.Iterator;
import jdk.nashorn.internal.runtime.regexp.joni.constants.OPCode;
import org.objectweb.asm.Label;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FrameNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.LookupSwitchInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TableSwitchInsnNode;
import org.objectweb.asm.tree.TryCatchBlockNode;
import org.objectweb.asm.tree.VarInsnNode;

/**
 * Inserts the bytecode instructions within method node, needed to handle the thrown listeners
 * registered.
 *
 * This helper turns the method node instructions of a method like this:
 * <br><pre>{@code
 * public Object foo(Object arg){
 *   return bar(arg);
 * }
 * </pre>
 * Into that:
 * <br><pre>{@code
 * public Object foo(Object arg){
 *   try{
 *     return bar(arg);
 *   } catch (Throwable th) {
 *     // Notify listeners that apply to this method
 *     Callback.onFinishThrowable(th, clazz, this, 0, arg);
 *     Callback.onFinishThrowable(th, clazz, this, 2, arg);
 *     Callback.onFinishThrowable(th, clazz, this, 10, arg);
 *     throw th;
 *   }
 * }
 * </pre>
 *
 * @author Ignacio del Valle Alles idelvall@shiftleft.io
 */
public class FinishedThrowableHelper extends Helper {


  public boolean addByteCodeInstructions(int methodId, ClassNode cn, MethodNode mn,
      ArrayList<Integer> hooksToUse) {

    ArrayList<Integer> listenersToUse = getListenersOfType(hooksToUse,
        FinishedThrowableListener.class);
    if (!isInstrumentationNeeded(listenersToUse)) {
      return false;
    }
    return addTryCatchInstructions(methodId, cn, mn, listenersToUse);
  }

  private boolean addTryCatchInstructions(int methodId, ClassNode cn, MethodNode mn,
      ArrayList<Integer> listenersToUse) {

    LabelNode startNode = new LabelNode();

    // Look for call to super constructor in the top frame (before any jump)
    if (mn.name.equals("<init>")) {
      InsnList il = mn.instructions;
      AbstractInsnNode superCall = null;
      AbstractInsnNode node = il.getFirst();
      int newCalls = 0;
      while (node != null) {
        if (node instanceof JumpInsnNode ||
            node instanceof TableSwitchInsnNode ||
            node instanceof LookupSwitchInsnNode) {
          // No branching supported before call to super()
          break;
        }
        if (node.getOpcode() == Opcodes.NEW) {
          newCalls++;
        }
        if (node instanceof MethodInsnNode && node.getOpcode() == Opcodes.INVOKESPECIAL) {
          MethodInsnNode min = (MethodInsnNode) node;
          if (min.name.equals("<init>")) {
            if (newCalls == 0) {
              superCall = min;
              break;
            } else {
              newCalls--;
            }
          }
        }
        node = node.getNext();
      }
      if (superCall == null) {
        // Weird constructor, not generated from Java Language
        Bctrace.getAgentLogger().log(Level.WARNING,
            "Could not add try/catch handler to constructor " + cn.name + "." + mn.name + mn.desc);
        return false;
      } else {
        // If super() call is found, then add the try/catch after that so the instance is initialized
        // https://docs.oracle.com/javase/specs/jvms/se7/html/jvms-4.html#jvms-4.10.1.9.invokespecial
        if (superCall.getNext() == null) {
          mn.instructions.add(startNode);
        } else {
          mn.instructions.insertBefore(superCall.getNext(), startNode);
        }
      }
    } else {
      mn.instructions.insert(startNode);
    }

    LabelNode endNode = new LabelNode();
    mn.instructions.add(endNode);

    InsnList il = new InsnList();
    Object[] parametersFrameTypes = ASMUtils.getParametersFrameTypes(cn, mn);
    il.add(new FrameNode(Opcodes.F_FULL, parametersFrameTypes.length, parametersFrameTypes, 1,
        new Object[]{"java/lang/Throwable"}));

    Label l = new Label();
    LabelNode handlerNode = new LabelNode();
    il.add(handlerNode);

    int thVarIndex = mn.maxLocals;
    mn.maxLocals = mn.maxLocals + 1;
    il.add(new VarInsnNode(Opcodes.ASTORE, thVarIndex));

    for (int i = 0; i < listenersToUse.size(); i++) {
      Integer index = listenersToUse.get(i);
      FinishedThrowableListener listener = (FinishedThrowableListener) bctrace.getHooks()[index]
          .getListener();
      if (listener.requiresThrowable()) {
        il.add(new VarInsnNode(Opcodes.ALOAD, thVarIndex));
      } else {
        il.add(new InsnNode(Opcodes.ACONST_NULL));
      }
      if (listener.requiresMethodId()) {
        il.add(ASMUtils.getPushInstruction(methodId)); // method id
      } else {
        il.add(ASMUtils.getPushInstruction(0));
      }
      if (listener.requiresClass()) {
        il.add(getClassConstantReference(Type.getObjectType(cn.name), cn.version));
      } else {
        il.add(new InsnNode(Opcodes.ACONST_NULL));
      }
      if (listener.requiresInstance()) {
        pushInstance(il, mn); // current instance
      } else {
        il.add(new InsnNode(Opcodes.ACONST_NULL));
      }
      il.add(ASMUtils.getPushInstruction(index)); // hook id
      if (listener.requiresArguments()) {
        pushMethodArgsArray(il, mn);
      } else {
        il.add(new InsnNode(Opcodes.ACONST_NULL));
      }
      il.add(new MethodInsnNode(Opcodes.INVOKESTATIC,
          "io/shiftleft/bctrace/runtime/Callback", "onFinishedThrowable",
          "(Ljava/lang/Throwable;ILjava/lang/Class;Ljava/lang/Object;I[Ljava/lang/Object;)V",
          false));
    }

    il.add(new VarInsnNode(Opcodes.ALOAD, thVarIndex));
    il.add(new InsnNode(Opcodes.ATHROW));

    TryCatchBlockNode blockNode = new TryCatchBlockNode(startNode, endNode, handlerNode, null);

    mn.tryCatchBlocks.add(blockNode);
    mn.instructions.add(il);
    return true;
  }
}
