// Razorpay Standard Checkout. The server creates the order and returns { keyId, razorpayOrderId, amount, currency }.
// The key SECRET never reaches the browser.
let loading

export function loadRazorpay() {
  if (window.Razorpay) return Promise.resolve()
  if (!loading) {
    loading = new Promise((resolve, reject) => {
      const s = document.createElement('script')
      s.src = 'https://checkout.razorpay.com/v1/checkout.js'
      s.onload = () => resolve()
      s.onerror = () => { loading = null; reject(new Error('Could not load Razorpay. Check your connection and try again.')) }
      document.body.appendChild(s)
    })
  }
  return loading
}

export async function openCheckout({ details, name, description, prefill, color, onSuccess, onDismiss }) {
  await loadRazorpay()
  const rzp = new window.Razorpay({
    key: details.keyId,
    amount: details.amount,
    currency: details.currency,
    order_id: details.razorpayOrderId,
    name,
    description,
    prefill,
    theme: color ? { color } : undefined,
    handler: onSuccess,                       // { razorpay_payment_id, razorpay_order_id, razorpay_signature }
    modal: { ondismiss: onDismiss },
  })
  rzp.open()
}

// Checkout's response -> what our server expects
export const toVerifyBody = (r) => ({
  razorpayPaymentId: r.razorpay_payment_id,
  razorpayOrderId: r.razorpay_order_id,
  razorpaySignature: r.razorpay_signature,
})
