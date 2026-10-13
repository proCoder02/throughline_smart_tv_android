import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

/// Lets a TV remote's D-pad leave a focused text field. Flutter's default
/// text-editing shortcuts claim every arrow key for caret movement, so
/// without this, focus is trapped in whichever field got it first. Sits in
/// MaterialApp.builder -- below those shortcuts in the tree, so it sees the
/// key first -- and covers every TextField in the app at once.
///
/// Up/Down always move focus out of a single-line field; in a multi-line
/// one, and for Left/Right everywhere, the caret has to already be at the
/// matching end of the text, so normal caret movement still works.
class DpadTextFieldEscape extends StatelessWidget {
  final Widget child;

  const DpadTextFieldEscape({super.key, required this.child});

  static final _directions = {
    LogicalKeyboardKey.arrowUp: TraversalDirection.up,
    LogicalKeyboardKey.arrowDown: TraversalDirection.down,
    LogicalKeyboardKey.arrowLeft: TraversalDirection.left,
    LogicalKeyboardKey.arrowRight: TraversalDirection.right,
  };

  KeyEventResult _onKeyEvent(FocusNode node, KeyEvent event) {
    if (event is KeyUpEvent) return KeyEventResult.ignored;
    final direction = _directions[event.logicalKey];
    if (direction == null) return KeyEventResult.ignored;

    final focused = FocusManager.instance.primaryFocus;
    final field = focused?.context?.findAncestorStateOfType<EditableTextState>();
    if (focused == null || field == null) return KeyEventResult.ignored;

    final value = field.textEditingValue;
    final selection = value.selection;
    final atStart = !selection.isValid ||
        (selection.isCollapsed && selection.baseOffset <= 0);
    final atEnd = !selection.isValid ||
        (selection.isCollapsed && selection.baseOffset >= value.text.length);
    final singleLine = field.widget.maxLines == 1;

    final canLeave = switch (direction) {
      TraversalDirection.up => singleLine || atStart,
      TraversalDirection.down => singleLine || atEnd,
      TraversalDirection.left => atStart,
      TraversalDirection.right => atEnd,
    };
    if (!canLeave) return KeyEventResult.ignored;

    return focused.focusInDirection(direction)
        ? KeyEventResult.handled
        : KeyEventResult.ignored;
  }

  @override
  Widget build(BuildContext context) {
    return Focus(
      canRequestFocus: false,
      skipTraversal: true,
      onKeyEvent: _onKeyEvent,
      child: child,
    );
  }
}
