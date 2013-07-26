/**
 * Copyright 2013 Alex Yanchenko
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *  
 *     http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License. 
 */
package org.droidparts.bus;

import static org.droidparts.inner.ClassSpecRegistry.getReceiveEventsSpecs;

import java.lang.ref.WeakReference;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Iterator;
import java.util.concurrent.ConcurrentHashMap;

import org.droidparts.inner.ann.MethodSpec;
import org.droidparts.inner.ann.bus.ReceiveEventsAnn;
import org.droidparts.util.L;

import android.os.Handler;
import android.os.Looper;

public class EventBus {

	private static final String ALL = "_all_";

	private static final ConcurrentHashMap<String, HashSet<EventReceiver<Object>>> actionToReceivers = new ConcurrentHashMap<String, HashSet<EventReceiver<Object>>>();

	public static void registerAnnotatedReceiver(Object obj) {
		MethodSpec<ReceiveEventsAnn>[] specs = getReceiveEventsSpecs(obj
				.getClass());
		for (MethodSpec<ReceiveEventsAnn> spec : specs) {
			ReflectiveReceiver receiver = new ReflectiveReceiver(obj,
					spec.method);
			registerReceiver(receiver, spec.ann.names);
		}
	}

	public static void unregisterAnnotatedReceiver(Object obj) {
		for (HashSet<EventReceiver<Object>> set : actionToReceivers.values()) {
			Iterator<EventReceiver<Object>> it = set.iterator();
			while (it.hasNext()) {
				EventReceiver<Object> rec = it.next();
				if (rec instanceof ReflectiveReceiver) {
					if (obj == ((ReflectiveReceiver) rec).objectRef.get()) {
						it.remove();
					}
				}
			}
		}
	}

	public static void registerReceiver(EventReceiver<?> receiver,
			String... eventNames) {
		@SuppressWarnings("unchecked")
		EventReceiver<Object> rec = (EventReceiver<Object>) receiver;
		if (eventNames.length == 0) {
			receiversForEventName(ALL).add(rec);
		} else {
			for (String action : eventNames) {
				receiversForEventName(action).add(rec);
			}
		}
	}

	public static void unregisterReceiver(EventReceiver<?> receiver) {
		receiversForEventName(ALL).remove(receiver);
		Iterator<String> it = actionToReceivers.keySet().iterator();
		while (it.hasNext()) {
			String action = it.next();
			HashSet<EventReceiver<Object>> set = actionToReceivers.get(action);
			set.remove(receiver);
			if (set.isEmpty()) {
				it.remove();
			}
		}

	}

	public static <T> void postEvent(final String name, final Object data) {
		Runnable r = new Runnable() {

			@Override
			public void run() {
				notifyReceivers(receiversForEventName(ALL), name, data);
				notifyReceivers(receiversForEventName(name), name, data);
			}
		};
		runOnUiThread(r);
	}

	private static HashSet<EventReceiver<Object>> receiversForEventName(
			String name) {
		HashSet<EventReceiver<Object>> set = actionToReceivers.get(name);
		if (set == null) {
			set = new HashSet<EventReceiver<Object>>();
			actionToReceivers.put(name, set);
		}
		return set;
	}

	private static void notifyReceivers(
			HashSet<EventReceiver<Object>> receivers, String event, Object data) {
		for (EventReceiver<Object> rec : receivers) {
			try {
				rec.onEvent(event, data);
			} catch (Exception e) {
				L.w(e);
				unregisterReceiver(rec);
			}
		}

	}

	private static void runOnUiThread(Runnable r) {
		if (handler == null) {
			handler = new Handler(Looper.getMainLooper());
		}
		handler.post(r);
	}

	private static Handler handler;

	private static class ReflectiveReceiver implements EventReceiver<Object> {

		final WeakReference<Object> objectRef;
		final Method method;

		ReflectiveReceiver(Object object, Method method) {
			objectRef = new WeakReference<Object>(object);
			this.method = method;
		}

		@Override
		public void onEvent(String name, Object data) {
			try {
				Object obj = objectRef.get();
				method.invoke(obj, name, data);
			} catch (Exception e) {
				throw new IllegalStateException(e);
			}
		}

	}

}
