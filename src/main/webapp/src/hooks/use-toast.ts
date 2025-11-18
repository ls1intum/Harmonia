import { toast as sonnerToast } from "sonner"

type ToastProps = {
  title?: string
  description?: string
  variant?: "default" | "destructive"
  action?: {
    label: string
    onClick: () => void
  }
}

function toast({ title, description, variant, action }: ToastProps) {
  const message = title || description || ""
  const descriptionText = title && description ? description : undefined

  if (variant === "destructive") {
    return sonnerToast.error(message, {
      description: descriptionText,
      action: action
          ? {
            label: action.label,
            onClick: action.onClick,
          }
          : undefined,
    })
  }

  return sonnerToast(message, {
    description: descriptionText,
    action: action
        ? {
          label: action.label,
          onClick: action.onClick,
        }
        : undefined,
  })
}

// Additional helper methods for convenience
toast.success = (message: string, description?: string) => {
  return sonnerToast.success(message, { description })
}

toast.error = (message: string, description?: string) => {
  return sonnerToast.error(message, { description })
}

toast.info = (message: string, description?: string) => {
  return sonnerToast.info(message, { description })
}

toast.warning = (message: string, description?: string) => {
  return sonnerToast.warning(message, { description })
}

toast.promise = sonnerToast.promise

toast.dismiss = (toastId?: string | number) => {
  sonnerToast.dismiss(toastId)
}

export { toast }