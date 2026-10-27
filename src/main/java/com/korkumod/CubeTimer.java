package com.korkumod;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class CubeTimer {
  private static class Item {
    int ticks;
    final Runnable action;

    Item(int ticks, Runnable action) {
      this.ticks = ticks;
      this.action = action;
    }
  }

  private final List<Item> items = new ArrayList<>();

  public void later(int ticks, Runnable action) {
    items.add(new Item(ticks, action));
  }

  public void tick() {
    if (items.isEmpty()) return;
    List<Runnable> due = new ArrayList<>();
    Iterator<Item> it = items.iterator();
    while (it.hasNext()) {
      Item i = it.next();
      if (--i.ticks <= 0) {
        due.add(i.action);
        it.remove();
      }
    }
    for (Runnable r : due) {
      r.run();
    }
  }
}
