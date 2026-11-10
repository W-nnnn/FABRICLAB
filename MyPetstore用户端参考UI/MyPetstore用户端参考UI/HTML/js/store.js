(function () {
  const cartTable = document.getElementById("cartTable");
  if (!cartTable) {
    return;
  }

  const toMoney = (n) => `$${n.toFixed(2)}`;

  function sanitizeQty(input) {
    let value = parseInt(input.value, 10);
    if (Number.isNaN(value) || value < 1) {
      value = 1;
    }
    if (value > 99) {
      value = 99;
    }
    input.value = String(value);
    return value;
  }

  function recalc() {
    const rows = cartTable.querySelectorAll("tbody tr");
    let itemCount = 0;
    let itemsTotal = 0;

    rows.forEach((row) => {
      const price = parseFloat(row.getAttribute("data-price") || "0");
      const qtyInput = row.querySelector('[data-role="qty"]');
      const lineNode = row.querySelector('[data-role="lineTotal"]');
      const selectInput = row.querySelector('[data-role="select-item"]');
      if (!qtyInput || !lineNode) {
        return;
      }
      const qty = sanitizeQty(qtyInput);
      const lineTotal = price * qty;
      lineNode.textContent = toMoney(lineTotal);
      const selected = !selectInput || selectInput.checked;
      row.classList.toggle("is-unselected", !selected);
      if (!selected) {
        return;
      }
      itemCount += qty;
      itemsTotal += lineTotal;
    });

    const shipping = itemCount > 0 ? 8 : 0;
    const discount = itemCount >= 3 ? 5 : 0;
    const grand = itemsTotal + shipping - discount;

    const itemCountNode = document.getElementById("itemCount");
    const itemsTotalNode = document.getElementById("itemsTotal");
    const grandTotalNode = document.getElementById("grandTotal");
    const shippingNode = document.getElementById("shippingTotal");
    const discountNode = document.getElementById("discountTotal");
    const couponNode = document.getElementById("couponStatus");
    const headerCountNode = document.getElementById("headerCartCount");
    const emptyNode = document.getElementById("cartEmpty");
    const checkoutNode = document.getElementById("checkoutButton");
    const selectAllNode = document.getElementById("selectAll");

    if (itemCountNode) {
      itemCountNode.textContent = String(itemCount);
    }
    if (itemsTotalNode) {
      itemsTotalNode.textContent = toMoney(itemsTotal);
    }
    if (grandTotalNode) {
      grandTotalNode.textContent = toMoney(grand);
    }
    if (shippingNode) {
      shippingNode.textContent = toMoney(shipping);
    }
    if (discountNode) {
      discountNode.textContent = discount > 0 ? `-${toMoney(discount)}` : toMoney(0);
    }
    if (couponNode) {
      couponNode.textContent = discount > 0 ? "已使用" : "满 3 件可用";
      couponNode.classList.toggle("is-pending", discount === 0);
    }
    if (headerCountNode) {
      headerCountNode.textContent = String(itemCount);
    }
    if (emptyNode) {
      emptyNode.hidden = rows.length > 0;
    }
    if (checkoutNode) {
      checkoutNode.classList.toggle("is-disabled", itemCount === 0);
      checkoutNode.setAttribute("aria-disabled", itemCount === 0 ? "true" : "false");
    }
    if (selectAllNode) {
      const itemChecks = cartTable.querySelectorAll('[data-role="select-item"]');
      const checkedCount = cartTable.querySelectorAll('[data-role="select-item"]:checked').length;
      selectAllNode.checked = itemChecks.length > 0 && checkedCount === itemChecks.length;
      selectAllNode.indeterminate = checkedCount > 0 && checkedCount < itemChecks.length;
    }
  }

  cartTable.addEventListener("click", (event) => {
    const target = event.target;
    if (!(target instanceof HTMLElement)) {
      return;
    }

    const action = target.getAttribute("data-action");
    if (!action) {
      return;
    }

    const row = target.closest("tr");
    if (!row) {
      return;
    }

    if (action === "remove") {
      event.preventDefault();
      row.remove();
      recalc();
      return;
    }

    const qtyInput = row.querySelector('[data-role="qty"]');
    if (!qtyInput) {
      return;
    }

    const current = sanitizeQty(qtyInput);
    if (action === "plus") {
      qtyInput.value = String(current + 1);
      recalc();
    }
    if (action === "minus") {
      qtyInput.value = String(Math.max(1, current - 1));
      recalc();
    }
  });

  cartTable.addEventListener("change", (event) => {
    const target = event.target;
    if (!(target instanceof HTMLElement)) {
      return;
    }
    if (target.getAttribute("data-role") === "qty") {
      recalc();
    }
    if (target.getAttribute("data-role") === "select-item") {
      recalc();
    }
  });

  const selectAll = document.getElementById("selectAll");
  if (selectAll) {
    selectAll.addEventListener("change", () => {
      cartTable.querySelectorAll('[data-role="select-item"]').forEach((input) => {
        input.checked = selectAll.checked;
      });
      recalc();
    });
  }

  recalc();
})();
